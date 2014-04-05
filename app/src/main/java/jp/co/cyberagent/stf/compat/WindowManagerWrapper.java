package jp.co.cyberagent.stf.compat;

import android.os.IBinder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class WindowManagerWrapper {
    private RotationInjector rotationInjector;
    private Object windowManager;

    private interface RotationInjector {
        public void freezeRotation(int rotation);
        public void thawRotation();
    }

    public WindowManagerWrapper() {
        windowManager = getWindowManager();

        try {
            rotationInjector = new FreezeThawRotationInjector();
        }
        catch (UnsupportedOperationException e) {
            rotationInjector = new SetRotationRotationInjector();
        }
    }

    public void freezeRotation(int rotation) {
        rotationInjector.freezeRotation(rotation);
    }

    public void thawRotation() {
        rotationInjector.thawRotation();
    }

    public static Object getWindowManager() {
        try {
            Object windowManagerBinder = ServiceManagerWrapper.getService("window");

            // We need to call IWindowManager.Stub.asInterface(IBinder obj) to get an instance
            // of IWindowManager
            Class<?> Stub = Class.forName("android.view.IWindowManager$Stub");

            Method asInterface = Stub.getMethod("asInterface", IBinder.class);

            return asInterface.invoke(null, windowManagerBinder);
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("WindowManager had ClassNotFoundException");
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("WindowManager had NoSuchMethodException");
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("WindowManager had IllegalAccessException");
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("WindowManager had InvocationTargetException");
        }
    }

    /**
     * EventInjector for SDK >10
     */
    private class FreezeThawRotationInjector implements RotationInjector {
        private Method freezeRotationInjector;
        private Method thawRotationInjector;

        public FreezeThawRotationInjector() {
            try {
                freezeRotationInjector = windowManager.getClass()
                        // public void freezeRotation(int rotation)
                        .getMethod("freezeRotation", int.class);

                thawRotationInjector = windowManager.getClass()
                        // public void thawRotation()
                        .getMethod("thawRotation");
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException("InputManagerEventInjector is not supported");
            }
        }

        public void freezeRotation(int rotation) {
            try {
                freezeRotationInjector.invoke(windowManager, rotation);
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        public void thawRotation() {
            try {
                thawRotationInjector.invoke(windowManager);
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * EventInjector for SDK <=10
     */
    private class SetRotationRotationInjector implements RotationInjector {
        private Method setRotationInjector;

        public SetRotationRotationInjector() {
            try {
                setRotationInjector = windowManager.getClass()
                        // void setRotation(int rotation, boolean alwaysSendConfiguration, int animFlags)
                        .getMethod("setRotation", int.class, boolean.class, int.class);
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException("InputManagerEventInjector is not supported");
            }
        }

        public void freezeRotation(int rotation) {
            try {
                setRotationInjector.invoke(windowManager, rotation, true, 0);
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        public void thawRotation() {
        }
    }
}
