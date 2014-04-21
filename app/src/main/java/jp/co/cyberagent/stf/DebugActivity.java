package jp.co.cyberagent.stf;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import jp.co.cyberagent.stf.util.BrowserUtil;
import jp.co.cyberagent.stf.util.NetworkUtil;

public class DebugActivity extends Activity {
    private static final String TAG = "DebugActivity";

    public static final String ACTION_IDENTITY = "jp.co.cyberagent.stf.ACTION_DEBUG";

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

        layout.setBackgroundColor(Color.BLUE);
        layout.setPadding(16, 16, 16, 16);
        layout.setGravity(Gravity.CENTER);

        PackageManager pm = getPackageManager();
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        ResolveInfo defaultBrowser = BrowserUtil.getDefaultBrowser(this);
        List<ResolveInfo> browsers = BrowserUtil.getBrowsers(this);

        for (ResolveInfo info : browsers) {
            String name = pm.getApplicationLabel(info.activityInfo.applicationInfo).toString();

            if (BrowserUtil.isSameBrowser(info, defaultBrowser)) {
                name += " (default)";
            }

            layout.addView(createLabel(name));
            layout.addView(createData(BrowserUtil.getComponent(info)));

        }

        layout.addView(createLabel("NETWORK"));
        layout.addView(createData(NetworkUtil.getNetworkType(tm.getNetworkType())));

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(layout);
    }

    private View createLabel(String text) {
        TextView titleView = new TextView(this);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(24f);
        titleView.setText(text);
        return titleView;
    }

    private View createData(String text) {
        TextView dataView = new TextView(this);
        dataView.setGravity(Gravity.CENTER);
        dataView.setTextColor(Color.WHITE);
        dataView.setTextSize(16f);
        dataView.setText(text);
        return dataView;
    }
}
