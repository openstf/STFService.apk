package jp.co.cyberagent.stf;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.*;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jp.co.cyberagent.stf.io.MessageReader;
import jp.co.cyberagent.stf.io.MessageRouter;
import jp.co.cyberagent.stf.io.MessageWriter;
import jp.co.cyberagent.stf.monitor.AirplaneModeMonitor;
import jp.co.cyberagent.stf.monitor.BatteryMonitor;
import jp.co.cyberagent.stf.monitor.ConnectivityMonitor;
import jp.co.cyberagent.stf.monitor.PhoneStateMonitor;
import jp.co.cyberagent.stf.monitor.RotationMonitor;
import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.query.DoIdentifyResponder;
import jp.co.cyberagent.stf.query.GetBrowsersResponder;
import jp.co.cyberagent.stf.query.GetClipboardResponder;
import jp.co.cyberagent.stf.query.GetPropertiesResponder;
import jp.co.cyberagent.stf.query.GetVersionResponder;
import jp.co.cyberagent.stf.query.SetClipboardResponder;
import jp.co.cyberagent.stf.query.SetKeyguardStateResponder;
import jp.co.cyberagent.stf.query.SetWakeLockResponder;

public class Service extends android.app.Service {
    public static final String ACTION_START = "jp.co.cyberagent.stf.ACTION_START";
    public static final String ACTION_STOP = "jp.co.cyberagent.stf.ACTION_STOP";
    public static final String EXTRA_PORT = "jp.co.cyberagent.stf.EXTRA_PORT";
    public static final String EXTRA_HOST = "jp.co.cyberagent.stf.EXTRA_HOST";
    public static final String EXTRA_BACKLOG = "jp.co.cyberagent.stf.EXTRA_BACKLOG";

    private static final String TAG = "STFService";
    private static final int NOTIFICATION_ID = 0x1;

    private ExecutorService executor = Executors.newCachedThreadPool();
    private boolean started = false;
    private MessageWriter.Pool writers = new MessageWriter.Pool();

    @Override
    public IBinder onBind(Intent intent) {
        // We don't support binding to this service
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Intent notificationIntent = new Intent(this, IdentityActivity.class);
        Notification notification = new NotificationCompat.Builder(this)
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

                int port = intent.getIntExtra(EXTRA_PORT, 1100);
                int backlog = intent.getIntExtra(EXTRA_BACKLOG, 1);

                String host = intent.getStringExtra(EXTRA_HOST);
                if (host == null) {
                    host = "127.0.0.1";
                }

                try {
                    ServerSocket acceptor = new ServerSocket(port, backlog, InetAddress.getByName(host));

                    executor.submit(new Server(acceptor));
                    executor.submit(new BatteryMonitor(this, writers));
                    executor.submit(new ConnectivityMonitor(this, writers));
                    executor.submit(new PhoneStateMonitor(this, writers));
                    executor.submit(new RotationMonitor(this, writers));
                    executor.submit(new AirplaneModeMonitor(this, writers));

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

    class Server extends Thread {
        private ServerSocket acceptor;
        private ExecutorService executor = Executors.newCachedThreadPool();

        public Server(ServerSocket acceptor) {
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
            Log.i(TAG, String.format("Server listening on %s:%d",
                    acceptor.getInetAddress().toString(),
                    acceptor.getLocalPort()
            ));

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
            private Socket socket;

            public Connection(Socket socket) {
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

                    router.register(Wire.RequestType.GET_BROWSERS,
                            new GetBrowsersResponder(getBaseContext()));

                    router.register(Wire.RequestType.GET_CLIPBOARD,
                            new GetClipboardResponder(getBaseContext()));

                    router.register(Wire.RequestType.GET_PROPERTIES,
                            new GetPropertiesResponder(getBaseContext()));

                    router.register(Wire.RequestType.DO_IDENTIFY,
                            new DoIdentifyResponder(getBaseContext()));

                    router.register(Wire.RequestType.SET_CLIPBOARD,
                            new SetClipboardResponder(getBaseContext()));

                    router.register(Wire.RequestType.SET_KEYGUARD_STATE,
                            new SetKeyguardStateResponder(getBaseContext()));

                    router.register(Wire.RequestType.SET_WAKE_LOCK,
                            new SetWakeLockResponder(getBaseContext()));

                    router.register(Wire.RequestType.GET_VERSION,
                            new GetVersionResponder(getBaseContext()));

                    while (!isInterrupted()) {
                        Wire.RequestEnvelope envelope = reader.read();

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
}
