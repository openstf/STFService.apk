package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.util.BrowserUtil;
import jp.co.cyberagent.stf.util.GraphicUtil;

public class GetBrowsersResponder extends AbstractResponder {
    public GetBrowsersResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetBrowsersRequest request =
                Wire.GetBrowsersRequest.parseFrom(envelope.getMessage());

        PackageManager pm = context.getPackageManager();

        List<ResolveInfo> allBrowsers = BrowserUtil.getBrowsers(context);
        ResolveInfo defaultBrowser = BrowserUtil.getDefaultBrowser(context);

        ArrayList<Wire.BrowserApp> apps = new ArrayList<Wire.BrowserApp>();

        for (ResolveInfo info : allBrowsers) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            apps.add(Wire.BrowserApp.newBuilder()
                    .setName(pm.getApplicationLabel(appInfo).toString())
                    .setComponent(BrowserUtil.getComponent(info))
                    .setSelected(BrowserUtil.isSameBrowser(info, defaultBrowser))
                    .setSystem((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    .build());
        }

        return Wire.Envelope.newBuilder()
            .setId(envelope.getId())
            .setType(Wire.MessageType.GET_BROWSERS)
            .setMessage(Wire.GetBrowsersResponse.newBuilder()
                .setSuccess(true)
                .setSelected(defaultBrowser != null)
                .addAllApps(apps)
                .build()
                .toByteString())
            .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
