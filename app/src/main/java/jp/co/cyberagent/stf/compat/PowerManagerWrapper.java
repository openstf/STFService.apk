package jp.co.cyberagent.stf.compat;

import android.os.SystemClock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jp.co.cyberagent.stf.util.InternalApi;

public class PowerManagerWrapper {
    private Object powerManager;
    private WakeInjector wakeInjector;

    public PowerManagerWrapper() {
        powerManager = InternalApi.getServiceAsInterface("power", "android.os.IPowerManager$Stub");

        try {
            wakeInjector = new WakeUpWakeInjector();
        }
        catch (UnsupportedOperationException e) {
            // Let it bubble
            wakeInjector = new UserActivityWakeInjector();
        }
    }

    public void wakeUp() {
        wakeInjector.wakeUp();
    }

    private interface WakeInjector {
        public boolean wakeUp();
    }

    /**
     * WakeInjector for newer API
     */
    private class WakeUpWakeInjector implements WakeInjector {
        private Method injector;

        public WakeUpWakeInjector() {
            try {
                injector = powerManager.getClass()
                        // public void wakeUp(long time) throws android.os.RemoteException
                        .getMethod("wakeUp", long.class);
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException("WakeUpWakeInjector is not supported");
            }
        }

        public boolean wakeUp() {
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
        private Method injector;

        public UserActivityWakeInjector() {
            try {
                injector = powerManager.getClass()
                        // public void userActivityWithForce(long when, boolean noChangeLights,
                        // boolean force) throws android.os.RemoteException
                        .getMethod("userActivityWithForce", long.class, boolean.class, boolean.class);
            }
            catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException("UserActivityWakeInjector is not supported");
            }
        }

        public boolean wakeUp() {
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
}
