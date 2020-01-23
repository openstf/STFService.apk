package jp.co.cyberagent.stf.compat;

import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jp.co.cyberagent.stf.util.InternalApi;

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

    public boolean injectInputEvent(InputEvent event) {
        return eventInjector.injectInputEvent(event);
    }

    private interface EventInjector {
        boolean injectKeyEvent(KeyEvent event);
        boolean injectInputEvent(InputEvent event);
    }

    /**
     * EventInjector for SDK >=16
     */
    private class InputManagerEventInjector implements EventInjector {
        public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
        private Object inputManager;
        private Method injector;

        public InputManagerEventInjector() {
            try {
                inputManager = InternalApi.getSingleton("android.hardware.input.InputManager");

                // injectInputEvent() is @hidden
                injector = inputManager.getClass()
                        // public boolean injectInputEvent(InputEvent event, int mode)
                        .getMethod("injectInputEvent", InputEvent.class, int.class);
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException("InputManagerEventInjector is not supported");
            }
        }

        public boolean injectKeyEvent(KeyEvent event) {
            return injectInputEvent(event);
        }

        @Override
        public boolean injectInputEvent(InputEvent event) {
            try {
                injector.invoke(inputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC);
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
                windowManager = WindowManagerWrapper.getWindowManager();

                keyInjector = windowManager.getClass()
                        // public boolean injectKeyEvent(android.view.KeyEvent ev, boolean sync)
                        // throws android.os.RemoteException
                        .getMethod("injectKeyEvent", KeyEvent.class, boolean.class);
            }
            catch (NoSuchMethodException e) {
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

        @Override
        public boolean injectInputEvent(InputEvent event) {
            return false;
        }
    }
}
