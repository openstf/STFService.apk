package jp.co.cyberagent.stf.input.agent;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.ClipData;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class InputService extends Service {
    private static final String TAG = "InputService";

    public static final String ACTION_START = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_START";
    public static final String ACTION_STOP = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_STOP";

    public static final String EXTRA_PORT = "port";

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
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            clientSocket.getInputStream()));
                    OutputStreamWriter writer = new OutputStreamWriter(
                            clientSocket.getOutputStream());

                    while (!isInterrupted()) {
                        String line = reader.readLine();

                        if (line == null) {
                            break;
                        }

                        if (line.equals("unlock")) {
                            unlock();
                            writer.write("OK\n");
                        }
                        else if (line.equals("lock")) {
                            lock();
                            writer.write("OK\n");
                        }
                        else if (line.equals("acquire wake lock")) {
                            acquireWakeLock();
                            writer.write("OK\n");
                        }
                        else if (line.equals("release wake lock")) {
                            releaseWakeLock();
                            writer.write("OK\n");
                        }
                        else if (line.equals("get clipboard")) {
                            CharSequence content = getClipboardContent();
                            if (content == null) {
                                writer.write("ERR:Clipboard has no content");
                            }
                            else {
                                writer.write("OK:" + content + "\n");
                            }
                        }
                        else if (line.equals("get phone number")) {
                            String phoneNumber = telephonyManager.getLine1Number();
                            if (phoneNumber == null || phoneNumber.isEmpty()) {
                                writer.write("ERR:No phone number\n");
                            }
                            else {
                                writer.write("OK:" + phoneNumber + "\n");
                            }
                        }
                        else if (line.equals("get imei")) {
                            String deviceId = telephonyManager.getDeviceId();
                            if (deviceId == null || deviceId.isEmpty()) {
                                writer.write("ERR:No IMEI\n");
                            }
                            else {
                                writer.write("OK:" + deviceId + "\n");
                            }
                        }
                        else if (line.equals("get operator")) {
                            String operator = telephonyManager.getSimOperatorName();
                            if (operator == null || operator.isEmpty()) {
                                writer.write("ERR:No operator\n");
                            }
                            else {
                                writer.write("OK:" + operator + "\n");
                            }
                        }
                        else if (line.startsWith("set clipboard ")) {
                            setClipboardContent(line.substring("set clipboard ".length()));
                            writer.write("OK\n");
                        }
                        else if (line.startsWith("show identity ")) {
                            showIdentity(line.substring("show identity ".length()));
                            writer.write("OK\n");
                        }
                        else {
                            writer.write("ERROR: unknown command\n");
                        }

                        writer.flush();
                    }
                }
                catch (IOException e) {
                }

                releaseWakeLock();
                lock();

                Log.i(TAG, "ClientThread closing");

                clients.remove(this);
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

            private CharSequence getClipboardContent() {
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

            private void setClipboardContent(String content) {
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
