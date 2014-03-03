package jp.co.cyberagent.stf.input.agent;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class InputService extends Service {
    private static final String TAG = "InputService";

    public static final String ACTION_UNLOCK = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_UNLOCK";
    public static final String ACTION_WAKE_LOCK_ACQUIRE = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_WAKE_LOCK_ACQUIRE";
    public static final String ACTION_WAKE_LOCK_RELEASE = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_WAKE_LOCK_RELEASE";
    public static final String ACTION_START = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_START";
    public static final String ACTION_STOP = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_STOP";

    public static final String EXTRA_PORT = "port";

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private AcceptorThread acceptor;

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String action = intent.getAction();
        if (ACTION_UNLOCK.equals(action)) {
            unlock();
        }
        else if (ACTION_WAKE_LOCK_ACQUIRE.equals(action)) {
            acquireWakeLock();
        }
        else if (ACTION_WAKE_LOCK_RELEASE.equals(action)) {
            releaseWakeLock();
        }
        else if (ACTION_START.equals(action)) {
            if (acceptor == null) {
                int port = intent.getIntExtra(EXTRA_PORT, 1100);
                acceptor = new AcceptorThread(port);
                acceptor.start();
            }
        }
        else if (ACTION_STOP.equals(action)) {
            if (acceptor != null) {
                acceptor.interrupt();
                acceptor = null;
            }
        }
        else {
            Log.e(TAG, "Unknown action " + action);
        }
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void unlock() {
        Log.i(TAG, "Unlocking device");
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        keyguardManager.newKeyguardLock("InputService").disableKeyguard();
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

    private class AcceptorThread extends Thread {
        private int port;
        private ServerSocket serverSocket;

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
                        client.start();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
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
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Log.i(TAG, "Closing InputService");
        }

        private class ClientThread extends Thread {
            private Socket clientSocket;

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

                acquireWakeLock();
                unlock();

                try {
                    BufferedReader input = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));

                    while (!isInterrupted()) {
                        String cmd = input.readLine();

                        if (cmd == null) {
                            break;
                        }
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

                releaseWakeLock();

                Log.i(TAG, "ClientThread closing");
            }
        }
    }
}
