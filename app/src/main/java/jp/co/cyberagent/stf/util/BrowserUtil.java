package jp.co.cyberagent.stf.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.List;

import jp.co.cyberagent.stf.R;

public class BrowserUtil {
    public static List<ResolveInfo> getBrowsers(Context context) {
        PackageManager pm = context.getPackageManager();

        Intent query = new Intent();
        query.setAction(Intent.ACTION_VIEW);
        query.setData(Uri.parse("http://localhost"));

        return pm.queryIntentActivities(query, 0);
    }

    public static ResolveInfo getDefaultBrowser(Context context) {
        PackageManager pm = context.getPackageManager();

        Intent query = new Intent();
        query.setAction(Intent.ACTION_VIEW);
        query.setData(Uri.parse("http://localhost"));

        ResolveInfo info = pm.resolveActivity(query, 0);

        if (info == null) {
            return info;
        }

        // Could be a Chooser
        if (info.activityInfo.packageName.equals("android")) {
            return null;
        }

        return info;
    }

    public static boolean isSameBrowser(ResolveInfo browserOne, ResolveInfo browserTwo) {
        return browserOne != null && browserTwo != null
                && browserOne.activityInfo != null && browserTwo.activityInfo != null
                && browserOne.activityInfo.packageName.equals(browserTwo.activityInfo.packageName)
                && browserOne.activityInfo.name.equals(browserTwo.activityInfo.name);
    }

    public static String getComponent(ResolveInfo info) {
        String packageName = info.activityInfo.packageName;
        String activityName = info.activityInfo.name;

        if (activityName.startsWith(packageName)) {
            return String.format("%s/%s", packageName, activityName.substring(packageName.length()));
        }

        return String.format("%s/%s", packageName, activityName);
    }
}
