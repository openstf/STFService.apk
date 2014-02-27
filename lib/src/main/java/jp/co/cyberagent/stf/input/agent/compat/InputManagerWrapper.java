package jp.co.cyberagent.stf.input.agent.compat;

import android.os.IBinder;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InputManagerWrapper {
    private EventInjector eventInjector;

    public InputManagerWrapper() {
        try {
            eventInjector = new InputManagerEventInjector();
        }
        catch (UnsupportedOperationException e) {
            eventInjector = new WindowManagerEventInjector();
        }
    }

    public boolean injectKeyEvent(KeyEvent event) {
        return eventInjector.injectKeyEvent(event);
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
                throw new UnsupportedOperationException("InputManagerEventInjector is not supported");
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException("InputManagerEventInjector is not supported");
            }
            catch (IllegalAccessException e) {
                throw new UnsupportedOperationException("InputManagerEventInjector is not supported");
            }
            catch (InvocationTargetException e) {
                throw new UnsupportedOperationException("InputManagerEventInjector is not supported");
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
                Object windowManagerBinder = ServiceManagerWrapper.getService("window");

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
                throw new UnsupportedOperationException("WindowManagerEventInjector is not supported");
            }
            catch (NoSuchMethodException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException("WindowManagerEventInjector is not supported");
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException("WindowManagerEventInjector is not supported");
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException("WindowManagerEventInjector is not supported");
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
