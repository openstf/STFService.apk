package jp.co.cyberagent.stf;

import android.graphics.Point;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Surface;

import java.io.IOException;
import java.net.UnknownHostException;

import jp.co.cyberagent.stf.compat.InputManagerWrapper;
import jp.co.cyberagent.stf.compat.PowerManagerWrapper;
import jp.co.cyberagent.stf.compat.WindowManagerWrapper;
import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.util.InternalApi;
import jp.co.cyberagent.stf.util.ProcUtil;

public class Agent extends Thread {
    public static final String PROCESS_NAME = "stf.agent";
    public static final String SOCKET = "stfagent";

    private InputManagerWrapper inputManager;
    private PowerManagerWrapper powerManager;
    private WindowManagerWrapper windowManager;
    private Handler handler;
    private LocalServerSocket serverSocket;
    private int deviceId = -1; // KeyCharacterMap.VIRTUAL_KEYBOARD
    private KeyCharacterMap keyCharacterMap;

    public static void main(String[] args) {
        ProcUtil.setArgV0(PROCESS_NAME);
        Looper.prepare();
        Handler handler = new Handler();
        for (String arg : args) {
            if (arg.equals("--version")) {
                System.out.println(BuildConfig.VERSION_NAME);
                return;
            }
            else if (arg.equals("--debug-info")) {
                printServiceDebugInfo();
                return;
            }
            else {
                System.err.println("Error: unknown argument " + arg);
                System.exit(1);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            /*
             * Android Q doesn't let minitouch manage inputs.
             * As a workaround, minitouch will forward events to this
             * agent which will be able to use the Android InputManager
             */
            System.out.println("Starting minitouch agent");
            Point size = MinitouchAgent.getScreenSize();
            MinitouchAgent m = new MinitouchAgent(size.x, size.y, handler);
            m.start();
        }
        new Agent(handler).start();
        Looper.loop();
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
            if (InternalApi.hasService(service)) {
                System.out.printf("FAIL: %s\n", service);
            }
            else {
                System.out.printf("OK: %s\n", service);
            }
        }
    }

    public Agent(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void run() {
        powerManager = new PowerManagerWrapper();
        inputManager = new InputManagerWrapper();
        windowManager = new WindowManagerWrapper();

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
            serverSocket = new LocalServerSocket(SOCKET);
            System.err.printf("Listening on @%s\n", SOCKET);
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
                LocalSocket clientSocket = serverSocket.accept();
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
        private LocalSocket clientSocket;

        public InputClient(LocalSocket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void interrupt() {
            try {
                clientSocket.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            System.err.println("InputClient started");

            try {
                while (!isInterrupted()) {
                    Wire.Envelope envelope =
                            Wire.Envelope.parseDelimitedFrom(clientSocket.getInputStream());

                    if (envelope == null) {
                        break;
                    }

                    switch (envelope.getType()) {
                        case DO_KEYEVENT:
                            handleKeyEventRequest(envelope);
                            break;
                        case DO_TYPE:
                            handleTypeRequest(envelope);
                            break;
                        case DO_WAKE:
                            handleWakeRequest(envelope);
                            break;
                        case SET_ROTATION:
                            handleSetRotationRequest(envelope);
                            break;
                        default:
                            System.err.printf("Unknown request type %d; maybe it's a Service call?\n", envelope.getType());
                    }
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            System.err.println("InputClient closing");
        }

        private void handleKeyEventRequest(Wire.Envelope envelope) throws IOException {
            Wire.KeyEventRequest request = Wire.KeyEventRequest.parseFrom(envelope.getMessage());

            int meta = 0;

            if (request.getShiftKey()) {
                meta |= KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON;
            }

            if (request.getCtrlKey()) {
                meta |= KeyEvent.META_CTRL_LEFT_ON | KeyEvent.META_CTRL_RIGHT_ON | KeyEvent.META_CTRL_ON;
            }

            if (request.getAltKey()) {
                meta |= KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_ALT_RIGHT_ON;
            }

            if (request.getMetaKey()) {
                meta |= meta |= KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_RIGHT_ON;
            }

            if (request.getSymKey()) {
                meta |= KeyEvent.META_SYM_ON;
            }

            if (request.getFunctionKey()) {
                meta |= KeyEvent.META_FUNCTION_ON;
            }

            if (request.getCapsLockKey()) {
                meta |= KeyEvent.META_CAPS_LOCK_ON;
            }

            if (request.getNumLockKey()) {
                meta |= KeyEvent.META_NUM_LOCK_ON;
            }

            if (request.getScrollLockKey()) {
                meta |= KeyEvent.META_SCROLL_LOCK_ON;
            }

            switch (request.getEvent()) {
                case DOWN:
                    keyDown(request.getKeyCode(), meta);
                    break;
                case UP:
                    keyUp(request.getKeyCode(), meta);
                    break;
                case PRESS:
                    keyPress(request.getKeyCode(), meta);
                    break;
            }
        }

        private void handleWakeRequest(Wire.Envelope envelope) throws IOException {
            Wire.DoWakeRequest request = Wire.DoWakeRequest.parseFrom(envelope.getMessage());
            wake();
        }

        private void handleTypeRequest(Wire.Envelope envelope) throws IOException {
            Wire.DoTypeRequest request = Wire.DoTypeRequest.parseFrom(envelope.getMessage());
            type(request.getText());
        }

        private void handleSetRotationRequest(Wire.Envelope envelope) throws IOException {
            Wire.SetRotationRequest request = Wire.SetRotationRequest.parseFrom(envelope.getMessage());

            switch (request.getRotation()) {
                case 0:
                    freezeRotation(Surface.ROTATION_0);
                    break;
                case 180:
                    freezeRotation(Surface.ROTATION_180);
                    break;
                case 270:
                    freezeRotation(Surface.ROTATION_270);
                    break;
                case 90:
                    freezeRotation(Surface.ROTATION_90);
                    break;
            }

            if (!request.getLock()) {
                thawRotation();
            }
        }

        private void keyDown(int keyCode, int metaState) {
            long time = SystemClock.uptimeMillis();
            handler.post( () -> inputManager.injectKeyEvent(new KeyEvent(
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
            )));
        }

        private void keyUp(int keyCode, int metaState) {
            long time = SystemClock.uptimeMillis();
            handler.post( ()-> inputManager.injectKeyEvent(new KeyEvent(
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
            )));
        }

        private void keyPress(int keyCode, int metaState) {
            keyDown(keyCode, metaState);
            keyUp(keyCode, metaState);
        }

        private void type(String text) {
            KeyEvent[] events = keyCharacterMap.getEvents(text.toCharArray());

            if (events != null) {
                for (KeyEvent event : events) {
                    handler.post( () -> inputManager.injectKeyEvent(event));
                }
            }
        }

        private void wake() {
            powerManager.wakeUp();
        }

        private void freezeRotation(int rotation) {
            windowManager.freezeRotation(rotation);
        }

        private void thawRotation() {
            windowManager.thawRotation();
        }
    }
}
