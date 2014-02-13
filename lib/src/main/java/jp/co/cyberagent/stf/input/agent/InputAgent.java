package jp.co.cyberagent.stf.input.agent;

import android.os.IBinder;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InputAgent {
    private EventInjector injector;

    public static void main(String[] args) {
        new InputAgent().run();
    }

    private void run() {
        injector = loadInjector();

        if (injector == null) {
            System.err.println("Unable to load any EventInjector");
            System.exit(1);
        }

        System.err.println("Happily waiting for input");
    }

    private EventInjector loadInjector() {
        try {
            return new InputManagerEventInjector();
        }
        catch (UnsupportedOperationException e) {
            System.err.println("InputManagerEventInjector is not supported");
        }

        try {
            return new WindowManagerEventInjector();
        }
        catch (UnsupportedOperationException e) {
            System.err.println("WindowManagerEventInjector is not supported");
        }

        return null;
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
