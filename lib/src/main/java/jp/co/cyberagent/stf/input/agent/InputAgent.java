package jp.co.cyberagent.stf.input.agent;

import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class InputAgent {
    // Mimics KeyCharacterMap.VIRTUAL_KEYBOARD, which is not available on lower SDK levels
    public static final int VIRTUAL_KEYBOARD = -1;
    public static final int PORT = 1090;

    private EventInjector injector;
    private ServerSocket serverSocket;

    public static void main(String[] args) {
        new InputAgent().run();
    }

    private void run() {
        loadInjector();
        startServer();
        waitForClients();
    }

    private void loadInjector() {
        try {
            injector = new InputManagerEventInjector();
            return;
        }
        catch (UnsupportedOperationException e) {
            System.err.println("InputManagerEventInjector is not supported");
        }

        try {
            injector = new WindowManagerEventInjector();
            return;
        }
        catch (UnsupportedOperationException e) {
            System.err.println("WindowManagerEventInjector is not supported");
        }

        System.err.println("Unable to load any EventInjector");
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
            injector.injectKeyEvent(new KeyEvent(
                    time,
                    time,
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    0,
                    metaState,
                    VIRTUAL_KEYBOARD,
                    0,
                    KeyEvent.FLAG_FROM_SYSTEM,
                    InputDevice.SOURCE_KEYBOARD
            ));
        }

        private void keyUp(int keyCode, int metaState) {
            long time = SystemClock.uptimeMillis();
            injector.injectKeyEvent(new KeyEvent(
                    time,
                    time,
                    KeyEvent.ACTION_UP,
                    keyCode,
                    0,
                    metaState,
                    VIRTUAL_KEYBOARD,
                    0,
                    KeyEvent.FLAG_FROM_SYSTEM,
                    InputDevice.SOURCE_KEYBOARD
            ));
        }

        private void keyPress(int keyCode, int metaState) {
            keyDown(keyCode, metaState);
            keyUp(keyCode, metaState);
        }

    }

    private interface EventInjector {
        public boolean injectKeyEvent(KeyEvent event);
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
                Class<?> inputManagerClass = this.getClass()
                        .getClassLoader()
                        .loadClass("android.hardware.input.InputManager");

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
                // The ServiceManager class is @hidden in newer SDKs
                Class<?> serviceManagerClass = this.getClass()
                        .getClassLoader()
                        .loadClass("android.os.ServiceManager");

                Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);

                Object windowManagerBinder = getServiceMethod.invoke(null, "window");

                // We need to call IWindowManager.Stub.asInterface(IBinder obj) to get an instance
                // of IWindowManager
                Class<?> stubClass = this.getClass()
                        .getClassLoader()
                        .loadClass("android.view.IWindowManager$Stub");

                Method asInterfaceMethod = stubClass.getMethod("asInterface", IBinder.class);

                windowManager = asInterfaceMethod.invoke(null, windowManagerBinder);

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
