package jp.co.cyberagent.stf;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.co.cyberagent.stf.util.BrowserUtil;
import jp.co.cyberagent.stf.util.GraphicUtil;

public class STFService extends Service {
    private static final String TAG = "STFService";

    public static final String ACTION_START = "jp.co.cyberagent.stf.ACTION_START";
    public static final String ACTION_STOP = "jp.co.cyberagent.stf.ACTION_STOP";

    public static final String EXTRA_PORT = "jp.co.cyberagent.stf.EXTRA_PORT";

    private static final int ONGOING_NOTIFICATION = 0x1;

    private PowerManager powerManager;
    private KeyguardManager keyguardManager;
    private TelephonyManager telephonyManager;
    private Object clipboardManagerObject;

    private AcceptorThread acceptor;

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        clipboardManagerObject = getSystemService(CLIPBOARD_SERVICE);

        Intent notificationIntent = new Intent(this, IdentityActivity.class);
        Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setTicker(getString(R.string.service_ticker))
                .setContentTitle(getString(R.string.service_title))
                .setContentText(getString(R.string.service_text))
                .setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0))
                .setWhen(System.currentTimeMillis())
                .build();

        startForeground(ONGOING_NOTIFICATION, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            if (acceptor == null) {
                int port = intent.getIntExtra(EXTRA_PORT, 1100);
                acceptor = new AcceptorThread(port);
                acceptor.start();
            }
        }
        else if (ACTION_STOP.equals(action)) {
            if (acceptor != null) {
                acceptor.interrupt();
                stopSelf();
            }
        }
        else {
            Log.e(TAG, "Unknown action " + action);
        }
        return START_STICKY;
    }

    private class AcceptorThread extends Thread {
        private int port;
        private ServerSocket serverSocket;
        private Set<ClientThread> clients = Collections.synchronizedSet(new HashSet<ClientThread>());

        public AcceptorThread(int port) {
            this.port = port;
        }

        @Override
        public void interrupt() {
            try {
                serverSocket.close();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Log.i(TAG, "Starting InputService on port " + port);

            try {
                serverSocket = new ServerSocket(port, 1,
                        InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));

                while (!isInterrupted()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientThread client = new ClientThread(clientSocket);
                        client.setDaemon(true);
                        clients.add(client);
                        client.start();
                    }
                    catch (IOException e) {
                        break;
                    }
                }
            }
            catch (UnknownHostException e) {
                e.printStackTrace();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                try {
                    if (serverSocket != null) {
                        serverSocket.close();
                    }

                    stopAll();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

            for (ClientThread client : clients) {
                client.interrupt();
            }

            Log.i(TAG, "Closing InputService");
        }

        private void stopAll() {
            for (ClientThread client : clients) {
                client.interrupt();
            }
        }

        private class ClientThread extends Thread {
            private Socket clientSocket;
            private PowerManager.WakeLock wakeLock;
            private KeyguardManager.KeyguardLock lock;

            public ClientThread(Socket clientSocket) {
                this.clientSocket = clientSocket;
            }

            @Override
            public void interrupt() {
                try {
                    clientSocket.close();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }

            @Override
            public void run() {
                Log.i(TAG, "Starting ClientThread");

                try {
                    while (!isInterrupted()) {
                        Wire.RequestEnvelope envelope =
                                Wire.RequestEnvelope.parseDelimitedFrom(clientSocket.getInputStream());

                        if (envelope == null) {
                            break;
                        }

                        switch (envelope.getType()) {
                            case VERSION:
                                handleVersionRequest(envelope);
                                break;
                            case SET_KEYGUARD_STATE:
                                handleSetKeyguardStateRequest(envelope);
                                break;
                            case SET_WAKE_LOCK:
                                handleSetWakeLockRequest(envelope);
                                break;
                            case SET_CLIPBOARD:
                                handleSetClipboardRequest(envelope);
                                break;
                            case GET_CLIPBOARD:
                                handleGetClipboardRequest(envelope);
                                break;
                            case GET_BROWSERS:
                                handleGetBrowsersRequest(envelope);
                                break;
                            case GET_PROPERTIES:
                                handleGetPropertiesRequest(envelope);
                                break;
                            case IDENTIFY:
                                handleIdentifyRequest(envelope);
                                break;
                            default:
                                Log.w(TAG, String.format("Unknown request type %d; maybe it's an Agent call?", envelope.getType()));
                        }
                    }
                }
                catch (IOException e) {
                }

                releaseWakeLock();
                lock();

                Log.i(TAG, "ClientThread closing");

                clients.remove(this);
            }

            private void handleVersionRequest(Wire.RequestEnvelope envelope) throws IOException {
                try {
                    PackageManager manager = getPackageManager();
                    PackageInfo info = manager.getPackageInfo(getPackageName(), 0);
                    Wire.VersionResponse.newBuilder()
                            .setSuccess(true)
                            .setVersion(info.versionName)
                            .build()
                            .writeDelimitedTo(clientSocket.getOutputStream());
                }
                catch (PackageManager.NameNotFoundException e) {
                    Wire.VersionResponse.newBuilder()
                            .setSuccess(false)
                            .build()
                            .writeDelimitedTo(clientSocket.getOutputStream());
                }
            }

            private void handleSetKeyguardStateRequest(Wire.RequestEnvelope envelope) throws IOException {
                Wire.SetKeyguardStateRequest request =
                        Wire.SetKeyguardStateRequest.parseFrom(envelope.getRequest());

                if (request.getEnabled()) {
                    lock();
                }
                else {
                    unlock();
                }

                Wire.SetKeyguardStateResponse.newBuilder()
                        .setSuccess(true)
                        .build()
                        .writeDelimitedTo(clientSocket.getOutputStream());
            }

            private void handleSetWakeLockRequest(Wire.RequestEnvelope envelope) throws IOException {
                Wire.SetWakeLockRequest request =
                        Wire.SetWakeLockRequest.parseFrom(envelope.getRequest());

                if (request.getEnabled()) {
                    acquireWakeLock();
                }
                else {
                    releaseWakeLock();
                }

                Wire.SetWakeLockResponse.newBuilder()
                        .setSuccess(true)
                        .build()
                        .writeDelimitedTo(clientSocket.getOutputStream());
            }

            private void handleSetClipboardRequest(Wire.RequestEnvelope envelope) throws IOException {
                Wire.SetClipboardRequest request =
                        Wire.SetClipboardRequest.parseFrom(envelope.getRequest());

                switch (request.getType()) {
                    case TEXT:
                        setClipboardText(request.getText());
                        Wire.SetClipboardResponse.newBuilder()
                                .setSuccess(true)
                                .build()
                                .writeDelimitedTo(clientSocket.getOutputStream());
                        break;
                    default:
                        Wire.SetClipboardResponse.newBuilder()
                                .setSuccess(false)
                                .build()
                                .writeDelimitedTo(clientSocket.getOutputStream());
                }
            }

            private void handleGetClipboardRequest(Wire.RequestEnvelope envelope) throws IOException {
                Wire.GetClipboardRequest request =
                        Wire.GetClipboardRequest.parseFrom(envelope.getRequest());

                switch (request.getType()) {
                    case TEXT:
                        CharSequence text = getClipboardText();

                        if (text == null) {
                            Wire.GetClipboardResponse.newBuilder()
                                    .setSuccess(true)
                                    .build()
                                    .writeDelimitedTo(clientSocket.getOutputStream());
                        }
                        else {
                            Wire.GetClipboardResponse.newBuilder()
                                    .setSuccess(true)
                                    .setText(text.toString())
                                    .build()
                                    .writeDelimitedTo(clientSocket.getOutputStream());
                        }

                        break;
                    default:
                        Wire.GetClipboardResponse.newBuilder()
                                .setSuccess(false)
                                .build()
                                .writeDelimitedTo(clientSocket.getOutputStream());
                }
            }

            private void handleGetBrowsersRequest(Wire.RequestEnvelope envelope) throws IOException {
                Wire.GetBrowsersRequest request =
                        Wire.GetBrowsersRequest.parseFrom(envelope.getRequest());

                PackageManager pm = getPackageManager();

                List<ResolveInfo> allBrowsers = BrowserUtil.getBrowsers(getBaseContext());
                ResolveInfo defaultBrowser = BrowserUtil.getDefaultBrowser(getBaseContext());

                ArrayList<Wire.BrowserApp> apps = new ArrayList<Wire.BrowserApp>();

                for (ResolveInfo info : allBrowsers) {
                    apps.add(Wire.BrowserApp.newBuilder()
                            .setName(pm.getApplicationLabel(info.activityInfo.applicationInfo).toString())
                            .setComponent(String.format("%s/%s", info.activityInfo.packageName, info.activityInfo.name))
                            .setSelected(BrowserUtil.isSameBrowser(info, defaultBrowser))
                            .setIcon(GraphicUtil.drawableToPNGByteString(pm.getApplicationIcon(info.activityInfo.applicationInfo)))
                            .build());
                }

                Wire.GetBrowsersResponse.newBuilder()
                        .setSuccess(true)
                        .setSelected(defaultBrowser != null)
                        .addAllApps(apps)
                        .build()
                        .writeDelimitedTo(clientSocket.getOutputStream());
            }

            private void handleGetPropertiesRequest(Wire.RequestEnvelope envelope) throws IOException {
                Wire.GetPropertiesRequest request =
                        Wire.GetPropertiesRequest.parseFrom(envelope.getRequest());

                ArrayList<Wire.Property> properties = new ArrayList<Wire.Property>();

                for (String name : request.getPropertiesList()) {
                    if (name.equals("imei")) {
                        String deviceId = telephonyManager.getDeviceId();
                        if (deviceId != null && !deviceId.isEmpty()) {
                            properties.add(Wire.Property.newBuilder()
                                    .setName(name)
                                    .setValue(deviceId)
                                    .build());
                        }
                    }
                    else if (name.equals("phoneNumber")) {
                        String phoneNumber = telephonyManager.getLine1Number();
                        if (phoneNumber != null && !phoneNumber.isEmpty()) {
                            properties.add(Wire.Property.newBuilder()
                                    .setName(name)
                                    .setValue(phoneNumber)
                                    .build());
                        }
                    }
                    else if (name.equals("iccid")) {
                        String iccid = telephonyManager.getSimSerialNumber();
                        if (iccid != null && !iccid.isEmpty()) {
                            properties.add(Wire.Property.newBuilder()
                                    .setName(name)
                                    .setValue(iccid)
                                    .build());
                        }
                    }
                    else if (name.equals("operator")) {
                        String operator;
                        switch (telephonyManager.getPhoneType()) {
                            case TelephonyManager.PHONE_TYPE_CDMA:
                                operator = telephonyManager.getSimOperatorName();
                                break;
                            default:
                                operator = telephonyManager.getNetworkOperatorName();
                                if (operator == null || operator.isEmpty()) {
                                    operator = telephonyManager.getSimOperatorName();
                                }
                        }
                        if (operator != null && !operator.isEmpty()) {
                            properties.add(Wire.Property.newBuilder()
                                    .setName(name)
                                    .setValue(operator)
                                    .build());
                        }
                    }
                }

                Wire.GetPropertiesResponse.newBuilder()
                        .setSuccess(true)
                        .addAllProperties(properties)
                        .build()
                        .writeDelimitedTo(clientSocket.getOutputStream());
            }

            private void handleIdentifyRequest(Wire.RequestEnvelope envelope) throws IOException {
                Wire.IdentifyRequest request =
                        Wire.IdentifyRequest.parseFrom(envelope.getRequest());

                showIdentity(request.getSerial());

                Wire.IdentifyResponse.newBuilder()
                        .setSuccess(true)
                        .build()
                        .writeDelimitedTo(clientSocket.getOutputStream());
            }

            @SuppressWarnings("deprecation")
            private void unlock() {
                Log.i(TAG, "Unlocking device");
                if (lock == null) {
                    lock = keyguardManager.newKeyguardLock("InputService");
                }
                lock.reenableKeyguard();
                lock.disableKeyguard();
            }

            private void lock() {
                Log.i(TAG, "Locking device");
                if (lock != null) {
                    lock.reenableKeyguard();
                }
            }

            @SuppressWarnings("deprecation")
            private void acquireWakeLock() {
                releaseWakeLock();
                Log.i(TAG, "Acquiring wake lock");
                wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "InputService"
                );
                wakeLock.acquire();
            }

            private void releaseWakeLock() {
                if (wakeLock != null) {
                    Log.i(TAG, "Releasing wake lock");
                    wakeLock.release();
                    wakeLock = null;
                }
            }

            private void showIdentity(String serial) {
                Intent intent = new Intent(getBaseContext(), IdentityActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(IdentityActivity.EXTRA_SERIAL, serial);
                startActivity(intent);
            }

            private CharSequence getClipboardText() {
                if (Build.VERSION.SDK_INT >= 11) {
                    android.content.ClipboardManager clipboardManager =
                            (android.content.ClipboardManager) clipboardManagerObject;
                    if (clipboardManager.hasPrimaryClip()) {
                        ClipData clipData = clipboardManager.getPrimaryClip();
                        if (clipData.getItemCount() > 0) {
                            ClipData.Item clip = clipData.getItemAt(0);
                            return clip.coerceToText(getApplicationContext());
                        }
                        else {
                            return null;
                        }
                    }
                    else {
                        return null;
                    }
                }
                else {
                    android.text.ClipboardManager clipboardManager =
                            (android.text.ClipboardManager) clipboardManagerObject;
                    return clipboardManager.getText();
                }
            }

            private void setClipboardText(String content) {
                if (Build.VERSION.SDK_INT >= 11) {
                    android.content.ClipboardManager clipboardManager =
                            (android.content.ClipboardManager) clipboardManagerObject;
                    clipboardManager.setPrimaryClip(ClipData.newPlainText(null, content));
                }
                else {
                    android.text.ClipboardManager clipboardManager =
                            (android.text.ClipboardManager) clipboardManagerObject;
                    clipboardManager.setText(content);
                }
            }
        }
    }
}
