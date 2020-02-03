package jp.co.cyberagent.stf.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.io.MessageWritable;
import jp.co.cyberagent.stf.util.BrowserUtil;

public class BrowserPackageMonitor extends AbstractMonitor {
    private static final String TAG = "STFBrowserPkgMonitor";

    private Set<Browser> browsers = new HashSet<Browser>();

    public BrowserPackageMonitor(Context context, MessageWritable writer) {
        super(context, writer);
    }

    @Override
    public void run() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                String pkg = intent.getData().getEncodedSchemeSpecificPart();

                if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    Log.i(TAG, String.format("Package %s was added", pkg));
                }
                else if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                    Log.i(TAG, String.format("Package %s changed", pkg));
                }
                else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    Log.i(TAG, String.format("Package %s was removed", pkg));
                }

                report(writer, false);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");

        context.registerReceiver(receiver, filter);

        try {
            synchronized (this) {
                while (!isInterrupted()) {
                    wait();
                }
            }
        }
        catch (InterruptedException e) {
            // Okay
        }
        finally {
            context.unregisterReceiver(receiver);
        }
    }

    @Override
    public void peek(MessageWritable writer) {
        report(writer, true);
    }

    synchronized private void report(MessageWritable writer, boolean force) {
        PackageManager pm = context.getPackageManager();

        Set<Browser> removeBrowsers = new HashSet<Browser>(browsers);
        Set<Browser> newBrowsers = new HashSet<Browser>();

        List<ResolveInfo> browserInfoList = BrowserUtil.getBrowsers(context);
        ResolveInfo defaultBrowser = BrowserUtil.getDefaultBrowser(context);

        ArrayList<Wire.BrowserApp> apps = new ArrayList<Wire.BrowserApp>();

        for (ResolveInfo info : browserInfoList) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            Browser browser = new Browser(
                    pm.getApplicationLabel(appInfo).toString(),
                    BrowserUtil.getComponent(info),
                    BrowserUtil.isSameBrowser(info, defaultBrowser),
                    (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            );

            if (browsers.contains(browser)) {
                removeBrowsers.remove(browser);
            }
            else {
                newBrowsers.add(browser);
            }

            apps.add(Wire.BrowserApp.newBuilder()
                    .setName(browser.name)
                    .setComponent(browser.component)
                    .setSelected(browser.selected)
                    .setSystem(browser.system)
                    .build());
        }

        if (!force && removeBrowsers.isEmpty() && newBrowsers.isEmpty()) {
            return;
        }

        Log.i(TAG, "Browser list changed");
        browsers.removeAll(removeBrowsers);
        browsers.addAll(newBrowsers);

        writer.write(Wire.Envelope.newBuilder()
                .setType(Wire.MessageType.EVENT_BROWSER_PACKAGE)
                .setMessage(Wire.BrowserPackageEvent.newBuilder()
                        .setSelected(defaultBrowser != null)
                        .addAllApps(apps)
                        .build()
                        .toByteString())
                .build());
    }

    private static class Browser {
        private String name;
        private String component;
        private boolean selected;
        private boolean system;

        public Browser(String name, String component, boolean selected, boolean system) {
            this.name = name;
            this.component = component;
            this.selected = selected;
            this.system = system;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Browser browser = (Browser) o;

            if (selected != browser.selected) return false;
            if (!component.equals(browser.component)) return false;
            if (!name.equals(browser.name)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + component.hashCode();
            result = 31 * result + (selected ? 1 : 0);
            return result;
        }
    }
}
