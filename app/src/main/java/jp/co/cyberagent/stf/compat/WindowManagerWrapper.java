package jp.co.cyberagent.stf.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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

    public void watchRotation(final RotationWatcher watcher) {
        try {
            Class<?> IRotationWatcher = Class.forName("android.view.IRotationWatcher");

            Object windowManager = getWindowManager();

            Object proxy = Proxy.newProxyInstance(IRotationWatcher.getClassLoader(), new Class[]{IRotationWatcher}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    System.out.println("Invoked " + method.getName());
                    if (method.getName().equals("onRotationChanged")) {
                        watcher.onRotationChanged((Integer) args[0]);
                    }
                    return null;
                }
            });

            Method watchRotation = windowManager.getClass().getMethod("watchRotation", IRotationWatcher);

            watchRotation.invoke(windowManager, proxy);
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("watchRotation is not supported: " + e.getMessage());
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("watchRotation is not supported: " + e.getMessage());
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("watchRotation is not supported: " + e.getMessage());
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
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
