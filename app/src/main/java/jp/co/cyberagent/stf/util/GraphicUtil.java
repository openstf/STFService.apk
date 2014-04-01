package jp.co.cyberagent.stf.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;

public class GraphicUtil {

    public static String bitmapToDataUri(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        String base64 = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
        return "data:image/png;base64," + base64;
    }

    public static ByteString bitmapToPNGByteString(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return ByteString.copyFrom(out.toByteArray());
    }

    /**
     * From http://stackoverflow.com/a/10600736/1540573
     */
    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static String drawableToDataUri(Drawable drawable) {
        return bitmapToDataUri(drawableToBitmap(drawable));
    }

    public static ByteString drawableToPNGByteString(Drawable drawable) {
        return bitmapToPNGByteString(drawableToBitmap(drawable));
    }
}
