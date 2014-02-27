package jp.co.cyberagent.stf.input.agent.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ServiceManager {
    public static Object getService(String name) {
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
}
