package jp.co.cyberagent.stf;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jp.co.cyberagent.stf.io.MessageReader;
import jp.co.cyberagent.stf.io.MessageRouter;
import jp.co.cyberagent.stf.io.MessageWriter;
import jp.co.cyberagent.stf.monitor.AbstractMonitor;
import jp.co.cyberagent.stf.monitor.AirplaneModeMonitor;
import jp.co.cyberagent.stf.monitor.BatteryMonitor;
import jp.co.cyberagent.stf.monitor.BrowserPackageMonitor;
import jp.co.cyberagent.stf.monitor.ConnectivityMonitor;
import jp.co.cyberagent.stf.monitor.PhoneStateMonitor;
import jp.co.cyberagent.stf.monitor.RotationMonitor;
import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.query.DoAddAccountMenuResponder;
import jp.co.cyberagent.stf.query.DoIdentifyResponder;
import jp.co.cyberagent.stf.query.DoRemoveAccountResponder;
import jp.co.cyberagent.stf.query.GetAccountsResponder;
import jp.co.cyberagent.stf.query.GetBrowsersResponder;
import jp.co.cyberagent.stf.query.GetClipboardResponder;
import jp.co.cyberagent.stf.query.GetDisplayResponder;
import jp.co.cyberagent.stf.query.GetPropertiesResponder;
import jp.co.cyberagent.stf.query.GetRingerModeResponder;
import jp.co.cyberagent.stf.query.GetSdStatusResponder;
import jp.co.cyberagent.stf.query.GetVersionResponder;
import jp.co.cyberagent.stf.query.GetWifiStatusResponder;
import jp.co.cyberagent.stf.query.GetBluetoothStatusResponder;
import jp.co.cyberagent.stf.query.SetClipboardResponder;
import jp.co.cyberagent.stf.query.SetKeyguardStateResponder;
import jp.co.cyberagent.stf.query.SetMasterMuteResponder;
import jp.co.cyberagent.stf.query.SetRingerModeResponder;
import jp.co.cyberagent.stf.query.SetWakeLockResponder;
import jp.co.cyberagent.stf.query.SetWifiEnabledResponder;
import jp.co.cyberagent.stf.query.SetBluetoothEnabledResponder;

public class Service extends android.app.Service {
    public static final String ACTION_START = "jp.co.cyberagent.stf.ACTION_START";
    public static final String ACTION_STOP = "jp.co.cyberagent.stf.ACTION_STOP";
    public static final String EXTRA_SOCKET = "jp.co.cyberagent.stf.EXTRA_SOCKET";
    public static final String DEFAULT_SOCKET = "stfservice";

    private static final String TAG = "STFService";
    private static final int NOTIFICATION_ID = 0x1;

    private List<AbstractMonitor> monitors = new ArrayList<AbstractMonitor>();
    private ExecutorService executor = Executors.newCachedThreadPool();
    private LocalServerSocket acceptor;
    private boolean started = false;
    private MessageWriter.Pool writers = new MessageWriter.Pool();

    // We can only access CLIPBOARD_SERVICE from the main thread
    private static Object clipboardManager;

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
    }

    public static Object getClipboardManager() {
        return clipboardManager;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't support binding to this service
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE);

        String channelId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel("stf_channel", "STF Channel");
        } else {
            channelId = "";
        }

        Intent notificationIntent = new Intent(this, IdentityActivity.class);
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setTicker(getString(R.string.service_ticker))
                .setContentTitle(getString(R.string.service_title))
                .setContentText(getString(R.string.service_text))
                .setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0))
                .setWhen(System.currentTimeMillis())
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "Stopping service");

        stopForeground(true);

        if (acceptor != null) {
            try {
                acceptor.close();
            }
            catch (IOException e) {
                // We don't care
            }
        }

        try {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            // Too bad
        }
        finally {
            started = false;

            // Unfortunately, we have no way to clean up some Binder-based callbacks
            // (namely IRotationWatcher) on lower API levels without killing the process,
            // which will allow DeathRecipient to handle it on their side.
            Process.killProcess(Process.myPid());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            if (!started) {
                Log.i(TAG, "Starting service");

                String socketName = intent.getStringExtra(EXTRA_SOCKET);
                if (socketName == null) {
                    socketName = DEFAULT_SOCKET;
                }

                try {
                    acceptor = new LocalServerSocket(socketName);

                    addMonitor(new BatteryMonitor(this, writers));
                    addMonitor(new ConnectivityMonitor(this, writers));
                    addMonitor(new PhoneStateMonitor(this, writers));
                    addMonitor(new RotationMonitor(this, writers));
                    addMonitor(new AirplaneModeMonitor(this, writers));
                    addMonitor(new BrowserPackageMonitor(this, writers));

                    executor.submit(new Server(acceptor));
                    executor.submit(new AdbMonitor());

                    started = true;
                }
                catch (UnknownHostException e) {
                    Log.e(TAG, e.getMessage());
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else {
                Log.w(TAG, "Service is already running");
            }
        }
        else if (ACTION_STOP.equals(action)) {
            stopSelf();
        }
        else {
            Log.e(TAG, "Unknown action " + action);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onLowMemory() {
        Log.w(TAG, "Low memory");
    }

    private void addMonitor(AbstractMonitor monitor) {
        monitors.add(monitor);
        executor.submit(monitor);
    }

    class Server extends Thread {
        private LocalServerSocket acceptor;
        private ExecutorService executor = Executors.newCachedThreadPool();

        public Server(LocalServerSocket acceptor) {
            this.acceptor = acceptor;
        }

        @Override
        public void interrupt() {
            super.interrupt();

            try {
                acceptor.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            String socketName = acceptor.getLocalSocketAddress().getName();
            Log.i(TAG, String.format("Server listening on @%s", socketName));

            try {
                while (!isInterrupted()) {
                    Connection conn = new Connection(acceptor.accept());
                    executor.submit(conn);
                }
            }
            catch (IOException e) {
            }
            finally {
                Log.i(TAG, "Server stopping");

                try {
                    acceptor.close();
                }
                catch (IOException e) {
                }

                stopSelf();
            }
        }

        class Connection extends Thread {
            private LocalSocket socket;

            public Connection(LocalSocket socket) {
                this.socket = socket;
            }

            @Override
            public void interrupt() {
                super.interrupt();

                try {
                    socket.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void run() {
                Log.i(TAG, "Connection started");

                MessageWriter writer = null;
                MessageRouter router = null;

                try {
                    MessageReader reader = new MessageReader(socket.getInputStream());

                    writer = new MessageWriter(socket.getOutputStream());
                    writers.add(writer);

                    router = new MessageRouter(writer);

                    router.register(Wire.MessageType.DO_IDENTIFY,
                            new DoIdentifyResponder(getBaseContext()));

                    router.register(Wire.MessageType.DO_ADD_ACCOUNT_MENU,
                            new DoAddAccountMenuResponder(getBaseContext()));

                    router.register(Wire.MessageType.DO_REMOVE_ACCOUNT,
                            new DoRemoveAccountResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_ACCOUNTS,
                            new GetAccountsResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_BROWSERS,
                            new GetBrowsersResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_CLIPBOARD,
                            new GetClipboardResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_DISPLAY,
                            new GetDisplayResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_PROPERTIES,
                            new GetPropertiesResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_RINGER_MODE,
                            new GetRingerModeResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_SD_STATUS,
                            new GetSdStatusResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_VERSION,
                            new GetVersionResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_WIFI_STATUS,
                            new GetWifiStatusResponder(getBaseContext()));

                    router.register(Wire.MessageType.GET_BLUETOOTH_STATUS,
                        new GetBluetoothStatusResponder(getBaseContext()));

                    router.register(Wire.MessageType.SET_CLIPBOARD,
                            new SetClipboardResponder(getBaseContext()));

                    router.register(Wire.MessageType.SET_KEYGUARD_STATE,
                            new SetKeyguardStateResponder(getBaseContext()));

                    router.register(Wire.MessageType.SET_RINGER_MODE,
                            new SetRingerModeResponder(getBaseContext()));

                    router.register(Wire.MessageType.SET_WAKE_LOCK,
                            new SetWakeLockResponder(getBaseContext()));

                    router.register(Wire.MessageType.SET_WIFI_ENABLED,
                            new SetWifiEnabledResponder(getBaseContext()));

                    router.register(Wire.MessageType.SET_BLUETOOTH_ENABLED,
                        new SetBluetoothEnabledResponder(getBaseContext()));

                    router.register(Wire.MessageType.SET_MASTER_MUTE,
                            new SetMasterMuteResponder(getBaseContext()));

                    for (AbstractMonitor monitor : monitors) {
                        monitor.peek(writer);
                    }

                    while (!isInterrupted()) {
                        Wire.Envelope envelope = reader.read();

                        if (envelope == null) {
                            break;
                        }

                        router.route(envelope);
                    }
                }
                catch (InvalidProtocolBufferException e) {
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                }
                catch (IOException e) {
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                finally {
                    Log.i(TAG, "Connection stopping");

                    writers.remove(writer);

                    if (router != null) {
                        router.cleanup();
                    }

                    try {
                        socket.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
    }

    /**
     * Monitors the adb state by checking /sys/class/android_usb/android0/state
     * <p>
     * If state is DISCONNECTED then IdentityActivity will be shown to allow easier identification
     * of malfunctioning devices
     */
    private class AdbMonitor extends Thread {
        // If something goes wrong you have 30 seconds to kill the STFService
        // Using smaller numbers will basically lock the devices until usb connection is established
        private static final int INTERVAL_MS = 30000;

        @Override
        public void run() {
            Log.d(TAG, "Starting adb monitor thread");
            String lastUsbState = "";
            String lastAdbState = "";
            try {
                while (!isInterrupted()) {
                    /**
                     * In order for the monitor to work you need to grant DUMP permission for
                     * the apk, e.g.
                     *
                     * adb -d shell pm grant jp.co.cyberagent.stf android.permission.DUMP
                     */
                    int dumpPermission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.DUMP);
                    if (dumpPermission == PackageManager.PERMISSION_GRANTED) {
                        String[] cmd = {
                            "/system/bin/dumpsys",
                            "usb"
                        };

                        java.lang.Process process = Runtime.getRuntime().exec(cmd);

                        /**
                         * If the output of the command will change then by default device will be
                         * considered connected
                         */
                        String currentUsbState = "";
                        String currentAdbState = "";
                        BufferedReader adbdStateReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        for (String line = adbdStateReader.readLine(); line != null; line = adbdStateReader.readLine()) {
                            if (line.contains("Current Functions:") || line.contains("mCurrentFunctions:")) {
                                currentAdbState = line.split(":").length == 2 ? line.split(":")[1] : "";
                            } else if (line.contains("Kernel state:")) {
                                currentUsbState = line.split(":").length == 2 ? line.split(":")[1] : "";
                            }
                        }

                        if (!currentUsbState.equals(lastUsbState)) {
                            Log.d(TAG, "Kernel state changed to" + currentUsbState);
                            lastUsbState = currentUsbState;
                        }

                        if (!currentAdbState.equals(lastAdbState)) {
                            Log.d(TAG, "adb state changed to" + currentAdbState);
                            lastAdbState = currentAdbState;
                        }

                        boolean disconnected = lastUsbState.contains("DISCONNECTED");
                        boolean adbEnabled = lastAdbState.contains("adb");
                        if (disconnected || !adbEnabled) {
                            Log.d(TAG, "Start activity for STFService");
                            getApplication().startActivity(new IdentityActivity.IntentBuilder().build(getApplication()));
                        }

                        adbdStateReader.close();
                        process.waitFor();
                    }

                    Thread.sleep(INTERVAL_MS);
                }
            } catch (IOException e) {
                Log.e(TAG, "IO error during exec of adb monitor", e);
            } catch (InterruptedException e) {
                Log.i(TAG, "Adb monitor thread interrupted");
            }
        }
    }
}
