package jp.co.cyberagent.stf.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import android.os.RemoteException;
import android.view.IRotationWatcher;

import jp.co.cyberagent.stf.util.InternalApi;

public class WindowManagerWrapper {
    private RotationInjector rotationInjector;
    private Object windowManager;

    private interface RotationInjector {
        public void freezeRotation(int rotation);
        public void thawRotation();
    }

    public static interface RotationWatcher {
        public void onRotationChanged(int rotation);
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

    // It's not clear why we're relying on reflection instead of using
    // Display.getRotation(). For now, reflection is used to ensure backwards
    // compatibility just in case Display.getRotation() behaves differently
    // in some cases. It's quite possible that IWindowManager.getRotation()
    // was only used because it was convenient to access from where it was
    // used, though.
    public int getRotation() {
        try {
            Method getter = windowManager.getClass().getMethod("getDefaultDisplayRotation");
            return (Integer) getter.invoke(windowManager);
        }
        catch (NoSuchMethodException e) {}
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        try {
            Method getter = windowManager.getClass().getMethod("getRotation");
            return (Integer) getter.invoke(windowManager);
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public Object watchRotation(final RotationWatcher watcher) {
        IRotationWatcher realWatcher = new IRotationWatcher.Stub() {
            @Override
            public void onRotationChanged(int rotation) throws RemoteException {
                watcher.onRotationChanged(rotation);
            }
        };

        try {
            Method getter = windowManager.getClass().getMethod("watchRotation", IRotationWatcher.class, int.class);
            getter.invoke(windowManager, realWatcher, 0);
            return realWatcher;
        }
        catch (NoSuchMethodException e) {
            try {
                Method getter = windowManager.getClass().getMethod("watchRotation", IRotationWatcher.class);
                getter.invoke(windowManager, realWatcher);
                return realWatcher;
            }
            catch (NoSuchMethodException e2) {
                throw new UnsupportedOperationException("watchRotation is not supported: " + e2.getMessage());
            }
            catch (IllegalAccessException e2) {
                throw new UnsupportedOperationException("watchRotation is not supported: " + e2.getMessage());
            }
            catch (InvocationTargetException e2) {
                throw new UnsupportedOperationException("watchRotation is not supported: " + e2.getMessage());
            }
        }
        catch (IllegalAccessException e) {
            throw new UnsupportedOperationException("watchRotation is not supported: " + e.getMessage());
        }
        catch (InvocationTargetException e) {
            throw new UnsupportedOperationException("watchRotation is not supported: " + e.getMessage());
        }
    }

    public static Object getWindowManager() {
        return InternalApi.getServiceAsInterface("window", "android.view.IWindowManager$Stub");
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
