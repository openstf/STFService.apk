package jp.co.cyberagent.stf.compat;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.IWindowManager;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * Created by Stanley Huang on 2016/11/21.
 */
public class ScreenshotManagerWrapper {

    private static String SURFACE_CONTROL_CLASS = "android.view.SurfaceControl";

    private static String SURFACE_CLASS = "android.view.Surface";

    private Method injector;

    private final IWindowManager wm;

    public ScreenshotManagerWrapper() {
        IBinder wmbinder = ServiceManager.getService("window");
        wm = IWindowManager.Stub.asInterface(wmbinder);
        if (Build.VERSION.SDK_INT <= 17) {
            injector = new OldApiScreenshotInjector().injectorMethod();
        } else {
            injector = new NewApiScreenshotInjector().injectorMethod();
        }
    }

    private interface ScreenshotInjector {
        public Method injectorMethod();
    }

    public byte[] screenshot() {
        byte[] bmpArray = null;
        try {
            int rotation = wm.getRotation();
            Matrix m = new Matrix();
            if (rotation == 1) {
                m.postRotate(-90.0f);
            } else if (rotation == 2) {
                m.postRotate(-180.0f);
            } else if (rotation == 3) {
                m.postRotate(-270.0f);
            }
            Bitmap bmp = (Bitmap) injector.invoke(null, new Object[]{Integer.valueOf(0), Integer.valueOf(0)});
            if (rotation != 0) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, false);
            }
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, bout);
            bmpArray = bout.toByteArray();
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return bmpArray;
    }


    private class NewApiScreenshotInjector implements ScreenshotInjector {
        private Method injector;

        public Method injectorMethod() {
            try {
                injector = Class.forName(SURFACE_CONTROL_CLASS).getDeclaredMethod("screenshot",
                        new Class[]{Integer.TYPE, Integer.TYPE});
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return injector;
        }
    }

    private class OldApiScreenshotInjector implements ScreenshotInjector {
        private Method injector;

        public Method injectorMethod() {
            try {
                injector = Class.forName(SURFACE_CLASS).getDeclaredMethod("screenshot",
                        new Class[]{Integer.TYPE, Integer.TYPE});
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            return injector;
        }
    }

}
