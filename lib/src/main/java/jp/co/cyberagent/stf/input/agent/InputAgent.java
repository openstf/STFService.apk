package jp.co.cyberagent.stf.input.agent;

import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class InputAgent {
    public static final int VERSION = 1;
    public static final int PORT = 1090;

    private EventInjector eventInjector;
    private WakeInjector wakeInjector;
    private ServerSocket serverSocket;
    private int deviceId = -1; // KeyCharacterMap.VIRTUAL_KEYBOARD
    private KeyCharacterMap keyCharacterMap;

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.equals("--version")) {
                System.out.println(VERSION);
                return;
            }
        }

        new InputAgent().run();
    }

    private void run() {
        selectDevice();
        loadKeyCharacterMap();
        loadEventInjector();
        loadWakeInjector();
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

    private void loadEventInjector() {
        try {
            eventInjector = new InputManagerEventInjector();
            return;
        }
        catch (UnsupportedOperationException e) {
            System.err.println("InputManagerEventInjector is not supported");
        }

        try {
            eventInjector = new WindowManagerEventInjector();
            return;
        }
        catch (UnsupportedOperationException e) {
            System.err.println("WindowManagerEventInjector is not supported");
        }

        System.err.println("Unable to load any EventInjector");
        System.exit(1);
    }

    private void loadWakeInjector() {
        try {
            wakeInjector = new WakeUpWakeInjector();
            return;
        }
        catch (UnsupportedOperationException e) {
            System.err.println("WakeUpWakeInjector is not supported");
        }

        try {
            wakeInjector = new UserActivityWakeInjector();
            return;
        }
        catch (UnsupportedOperationException e) {
            System.err.println("UserActivityWakeInjector is not supported");
        }

        System.err.println("Unable to load any WakeInjector");
        System.exit(1);
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
                    InputAgentProtocol.InputEvent inEvent =
                            InputAgentProtocol.InputEvent.parseDelimitedFrom(socket.getInputStream());

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
            eventInjector.injectKeyEvent(new KeyEvent(
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
            eventInjector.injectKeyEvent(new KeyEvent(
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
                    eventInjector.injectKeyEvent(event);
                }
            }
        }

        private void wake() {
            wakeInjector.wake();
        }
    }

    private Object getService(String name) {
        try {
            // The ServiceManager class is @hidden in newer SDKs
            Class<?> ServiceManager = Class.forName("android.os.ServiceManager");
            Method getService = ServiceManager.getMethod("getService", String.class);
            return getService.invoke(null, name);
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }

    private interface EventInjector {
        public boolean injectKeyEvent(KeyEvent event);
    }

    private interface WakeInjector {
        public boolean wake();
    }

    /**
     * WakeInjector for newer API
     */
    private class WakeUpWakeInjector implements WakeInjector {
        private Object powerManager;
        private Method injector;

        public WakeUpWakeInjector() {
            try {
                Object powerManagerBinder = getService("power");
                Class<?> Stub = Class.forName("android.os.IPowerManager$Stub");
                Method asInterface = Stub.getMethod("asInterface", IBinder.class);

                powerManager = asInterface.invoke(null, powerManagerBinder);

                injector = powerManager.getClass()
                        // public void wakeUp(long time) throws android.os.RemoteException
                        .getMethod("wakeUp", long.class);
            }
            catch (ClassNotFoundException e) {
                throw new UnsupportedOperationException();
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException();
            }
            catch (IllegalAccessException e) {
                throw new UnsupportedOperationException();
            }
            catch (InvocationTargetException e) {
                throw new UnsupportedOperationException();
            }
        }

        public boolean wake() {
            try {
                injector.invoke(powerManager, SystemClock.uptimeMillis());
                return true;
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
                return false;
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    /**
     * WakeInjector for older API
     */
    private class UserActivityWakeInjector implements WakeInjector {
        private Object powerManager;
        private Method injector;

        public UserActivityWakeInjector() {
            try {
                Object powerManagerBinder = getService("power");
                Class<?> Stub = Class.forName("android.os.IPowerManager$Stub");
                Method asInterface = Stub.getMethod("asInterface", IBinder.class);

                powerManager = asInterface.invoke(null, powerManagerBinder);

                injector = powerManager.getClass()
                        // public void userActivityWithForce(long when, boolean noChangeLights,
                        // boolean force) throws android.os.RemoteException
                        .getMethod("userActivityWithForce", long.class, boolean.class, boolean.class);
            }
            catch (ClassNotFoundException e) {
                throw new UnsupportedOperationException();
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException();
            }
            catch (IllegalAccessException e) {
                throw new UnsupportedOperationException();
            }
            catch (InvocationTargetException e) {
                throw new UnsupportedOperationException();
            }
        }

        public boolean wake() {
            try {
                injector.invoke(powerManager, SystemClock.uptimeMillis(), false, true);
                return true;
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
                return false;
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    /**
     * EventInjector for SDK >=16
     */
    private class InputManagerEventInjector implements EventInjector {
        private Object inputManager;
        private Method injector;

        public InputManagerEventInjector() {
            try {
                // The InputManager class is public, but only since SDK 16
                Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");

                // getInstance() is @hidden
                Method getInstanceMethod = inputManagerClass.getMethod("getInstance");

                inputManager = getInstanceMethod.invoke(null);

                // injectInputEvent() is @hidden
                injector = inputManagerClass
                        // public boolean injectInputEvent(InputEvent event, int mode)
                        .getMethod("injectInputEvent", InputEvent.class, int.class);
            }
            catch (ClassNotFoundException e) {
                throw new UnsupportedOperationException();
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException();
            }
            catch (IllegalAccessException e) {
                throw new UnsupportedOperationException();
            }
            catch (InvocationTargetException e) {
                throw new UnsupportedOperationException();
            }
        }

        public boolean injectKeyEvent(KeyEvent event) {
            try {
                injector.invoke(inputManager, event, 0);
                return true;
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
                return false;
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    /**
     * EventInjector for SDK <16
     */
    private class WindowManagerEventInjector implements EventInjector {
        private Object windowManager;
        private Method keyInjector;

        public WindowManagerEventInjector() {
            try {
                Object windowManagerBinder = getService("window");

                // We need to call IWindowManager.Stub.asInterface(IBinder obj) to get an instance
                // of IWindowManager
                Class<?> Stub = Class.forName("android.view.IWindowManager$Stub");

                Method asInterface = Stub.getMethod("asInterface", IBinder.class);

                windowManager = asInterface.invoke(null, windowManagerBinder);

                keyInjector = windowManager.getClass()
                        // public boolean injectKeyEvent(android.view.KeyEvent ev, boolean sync)
                        // throws android.os.RemoteException
                        .getMethod("injectKeyEvent", KeyEvent.class, boolean.class);
            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException();
            }
            catch (NoSuchMethodException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException();
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException();
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException();
            }
        }

        public boolean injectKeyEvent(KeyEvent event) {
            try {
                keyInjector.invoke(windowManager, event, false);
                return true;
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
                return false;
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
}
