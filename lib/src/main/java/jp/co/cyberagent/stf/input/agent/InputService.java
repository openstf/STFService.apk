package jp.co.cyberagent.stf.input.agent;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jp.co.cyberagent.stf.input.agent.proto.ServiceProto;

public class InputService extends Service {
    private static final String TAG = "InputService";

    public static final String ACTION_START = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_START";
    public static final String ACTION_STOP = "jp.co.cyberagent.stf.input.agent.InputService.ACTION_STOP";

    public static final String EXTRA_PORT = "port";

    private PowerManager powerManager;

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
        return START_NOT_STICKY;
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
                        ServiceProto.ServiceCall call = ServiceProto.ServiceCall.parseDelimitedFrom(
                                clientSocket.getInputStream());

                        if (call == null) {
                            break;
                        }

                        switch (call.getAction()) {
                            case UNLOCK:
                                unlock();
                                break;
                            case WAKE_LOCK_ACQUIRE:
                                acquireWakeLock();
                                break;
                            case WAKE_LOCK_RELEASE:
                                releaseWakeLock();
                                break;
                            case IDENTITY:
                                ServiceProto.IdentityServiceCall message = ServiceProto.IdentityServiceCall.parseFrom(call.getMessage());
                                if (message != null) {
                                    showIdentity(message.getSerial());
                                }
                                break;
                        }
                    }
                }
                catch (IOException e) {
                }

                releaseWakeLock();

                Log.i(TAG, "ClientThread closing");

                clients.remove(this);
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

            private void showIdentity(String serial) {
                Intent intent = new Intent(getBaseContext(), IdentityActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(IdentityActivity.EXTRA_SERIAL, serial);
                startActivity(intent);
            }
        }
    }
}
