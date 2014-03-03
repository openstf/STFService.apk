package jp.co.cyberagent.stf.input.agent;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import jp.co.cyberagent.stf.input.agent.compat.InputManagerWrapper;
import jp.co.cyberagent.stf.input.agent.compat.PowerManagerWrapper;
import jp.co.cyberagent.stf.input.agent.compat.ServiceManagerWrapper;
import jp.co.cyberagent.stf.input.agent.proto.AgentProto;

public class InputAgent {
    public static final int VERSION = 1;
    public static final int PORT = 1090;

    private InputManagerWrapper inputManager;
    private PowerManagerWrapper powerManager;
    private ServerSocket serverSocket;
    private int deviceId = -1; // KeyCharacterMap.VIRTUAL_KEYBOARD
    private KeyCharacterMap keyCharacterMap;

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.equals("--version")) {
                System.out.println(VERSION);
                return;
            }
            if (arg.equals("--debug-info")) {
                printServiceDebugInfo();
                return;
            }
        }

        new InputAgent().run();
    }

    private static void printServiceDebugInfo() {
        String[] services = {
                "accessibility",
                "account",
                "activity",
                "alarm",
                "audio",
                "bluetooth",
                "clipboard",
                "connectivity",
                "device_policy",
                "display",
                "download",
                "input_method",
                "input",
                "keyguard",
                "layout_inflater",
                "location",
                "media_router",
                "notification",
                "servicediscovery",
                "power",
                "search",
                "sensor",
                "storage",
                "phone",
                "textservices",
                "uimode",
                "user",
                "vibrator",
                "wallpaper",
                "wifip2p",
                "wifi",
                "window",
        };

        for (String service : services) {
            if (ServiceManagerWrapper.getService(service) == null) {
                System.out.printf("FAIL: %s\n", service);
            }
            else {
                System.out.printf("OK: %s\n", service);
            }
        }
    }

    private void run() {
        powerManager = new PowerManagerWrapper();
        inputManager = new InputManagerWrapper();

        selectDevice();
        loadKeyCharacterMap();
        startServer();
        waitForClients();
    }

    private void selectDevice() {
        try {
            deviceId = KeyCharacterMap.class.getDeclaredField("VIRTUAL_KEYBOARD")
                    .getInt(KeyCharacterMap.class);
        }
        catch (NoSuchFieldException e) {
            System.err.println("Falling back to KeyCharacterMap.BUILT_IN_KEYBOARD");
            deviceId = 0;
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void loadKeyCharacterMap() {
        keyCharacterMap = KeyCharacterMap.load(deviceId);
    }

    private void startServer() {
        try {
            serverSocket = new ServerSocket(PORT, 1,
                    InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));

            System.err.printf("Listening on port %d\n", PORT);
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        }
        catch (IOException e) {
            stopServer();
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void stopServer() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void waitForClients() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                InputClient client = new InputClient(clientSocket);
                client.start();
            }
            catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private class InputClient extends Thread {
        private Socket socket;

        public InputClient(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void interrupt() {
            try {
                socket.close();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            System.err.println("InputClient started");

            try {
                while (!isInterrupted()) {
                    AgentProto.InputEvent inEvent =
                            AgentProto.InputEvent.parseDelimitedFrom(socket.getInputStream());

                    if (inEvent == null) {
                        break;
                    }

                    int meta = 0;

                    if (inEvent.getShiftKey()) {
                        meta |= KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON;
                    }

                    if (inEvent.getCtrlKey()) {
                        meta |= KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_RIGHT_ON;
                    }

                    if (inEvent.getAltKey()) {
                        meta |= KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_RIGHT_ON;
                    }

                    if (inEvent.getMetaKey()) {
                        meta |= meta |= KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON;
                    }

                    if (inEvent.getSymKey()) {
                        meta |= KeyEvent.META_SYM_ON;
                    }

                    if (inEvent.getFunctionKey()) {
                        meta |= KeyEvent.META_FUNCTION_ON;
                    }

                    if (inEvent.getCapsLockKey()) {
                        meta |= KeyEvent.META_CAPS_LOCK_ON;
                    }

                    if (inEvent.getNumLockKey()) {
                        meta |= KeyEvent.META_NUM_LOCK_ON;
                    }

                    if (inEvent.getScrollLockKey()) {
                        meta |= KeyEvent.META_SCROLL_LOCK_ON;
                    }

                    switch (inEvent.getAction()) {
                        case KEYDOWN:
                            keyDown(inEvent.getKeyCode(), meta);
                            break;
                        case KEYUP:
                            keyUp(inEvent.getKeyCode(), meta);
                            break;
                        case KEYPRESS:
                            keyPress(inEvent.getKeyCode(), meta);
                            break;
                        case TYPE:
                            if (inEvent.hasText()) {
                                type(inEvent.getText());
                            }
                            break;
                        case WAKE:
                            wake();
                            break;
                    }
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            System.err.println("InputClient closing");
        }

        private void keyDown(int keyCode, int metaState) {
            long time = SystemClock.uptimeMillis();
            inputManager.injectKeyEvent(new KeyEvent(
                    time,
                    time,
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    0,
                    metaState,
                    deviceId,
                    0,
                    KeyEvent.FLAG_FROM_SYSTEM,
                    InputDevice.SOURCE_KEYBOARD
            ));
        }

        private void keyUp(int keyCode, int metaState) {
            long time = SystemClock.uptimeMillis();
            inputManager.injectKeyEvent(new KeyEvent(
                    time,
                    time,
                    KeyEvent.ACTION_UP,
                    keyCode,
                    0,
                    metaState,
                    deviceId,
                    0,
                    KeyEvent.FLAG_FROM_SYSTEM,
                    InputDevice.SOURCE_KEYBOARD
            ));
        }

        private void keyPress(int keyCode, int metaState) {
            keyDown(keyCode, metaState);
            keyUp(keyCode, metaState);
        }

        private void type(String text) {
            KeyEvent[] events = keyCharacterMap.getEvents(text.toCharArray());

            if (events != null) {
                for (KeyEvent event : events) {
                    inputManager.injectKeyEvent(event);
                }
            }
        }

        private void wake() {
            powerManager.wakeUp();
        }
    }
}
