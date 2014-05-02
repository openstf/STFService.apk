package jp.co.cyberagent.stf;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import jp.co.cyberagent.stf.util.GraphicUtil;

public class IconActivity extends Activity {
    private static final String TAG = "STFIconActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setKeepScreenOn(true);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        layout.setBackgroundColor(Color.WHITE);
        layout.setPadding(16, 16, 16, 16);
        layout.setGravity(Gravity.CENTER);

        try {
            PackageManager pm = getPackageManager();
            String pkg = getIntent().getData().getEncodedSchemeSpecificPart();
            Drawable icon = pm.getApplicationIcon(pkg);

            ImageView iconView = new ImageView(this);
            iconView.setImageDrawable(icon);
            layout.addView(iconView);

            File file = new File(getExternalFilesDir(null), pkg + ".png");
            FileOutputStream out = new FileOutputStream(file);
            GraphicUtil.drawableToPNGByteString(icon).writeTo(out);
            out.close();

            Log.i(TAG, String.format("Wrote icon to %s", file.getAbsolutePath()));
        }
        catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "No such package");
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to open output file");
        }
        catch (IOException e) {
            Log.e(TAG, "Unable to write to output file");
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(layout);
    }
}
