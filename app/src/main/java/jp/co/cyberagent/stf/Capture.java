package jp.co.cyberagent.stf;

import android.net.LocalServerSocket;
import android.net.LocalSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jp.co.cyberagent.stf.compat.ScreenshotManagerWrapper;
import jp.co.cyberagent.stf.util.ProcUtil;

public class Capture {
    public static final String PROCESS_NAME = "stf.capture";
    public static final String SOCKET = "javacap";

    private LocalServerSocket serverSocket;

    public Capture() {
    }

    public static void main(String[] args) {
        ProcUtil.setArgV0(PROCESS_NAME);

        for (String arg : args) {
            if (arg.equals("--version")) {
                System.out.println(Version.name);
                return;
            } else {
                System.err.println("Error: unknown argument " + arg);
                System.exit(1);
            }
        }

        new Capture().run();
    }

    private void run() {
        startServer();
        waitForClients();
    }

    private void startServer() {
        try {
            serverSocket = new LocalServerSocket(SOCKET);
            System.err.printf("Listening on @%s\n", SOCKET);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            stopServer();
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void stopServer() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void waitForClients() {
        while (true) {
            try {
                LocalSocket clientSocket = serverSocket.accept();
                Capture.InputClient client = new Capture.InputClient(clientSocket);
                client.start();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private class InputClient extends Thread {
        private LocalSocket clientSocket;
        private ScreenshotManagerWrapper screenshotManager;

        public InputClient(LocalSocket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void interrupt() {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            System.err.println("InputClient started");

            screenshotManager = new ScreenshotManagerWrapper();

            try {

                OutputStream out = clientSocket.getOutputStream();
                byte[] jpegData = screenshotManager.screenshot();
                ByteBuffer buf = ByteBuffer.allocate(24);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.put((byte) 0x01); // version
                buf.put((byte) 0x03); // length 3*8 bit
                buf.putInt(0); // pid
                buf.putInt(screenshotManager.getWidth()); // real width
                buf.putInt(screenshotManager.getHeight()); // real height
                buf.putInt(0); // virt width
                buf.putInt(0); // virt height
                buf.put((byte) 0x00); // display orientation
                buf.put((byte) 0x01); // quirk: QUIRK_DUMB
                buf.flip();

                out.write(buf.array());
                out.flush();

                while (true) {
                    // jpeg data size
                    buf = ByteBuffer.allocate(4);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    buf.putInt(jpegData.length);
                    buf.flip();
                    out.write(buf.array());
                    // jpeg data
                    out.write(jpegData);
                    out.flush();
                    jpegData = screenshotManager.screenshot();
                }
            } catch (IOException e) {
                System.out.println("I/O exception: " + e);
            }
            System.err.println("InputClient closing");
        }
    }
}
