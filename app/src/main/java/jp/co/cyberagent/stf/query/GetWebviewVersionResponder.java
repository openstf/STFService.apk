package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.protobuf.GeneratedMessageLite;

import java.util.ArrayList;
import java.util.List;

import jp.co.cyberagent.stf.BuildConfig;
import jp.co.cyberagent.stf.IdentityActivity;
import jp.co.cyberagent.stf.proto.Wire;

import static android.content.ContentValues.TAG;

public class GetWebviewVersionResponder extends AbstractResponder {

    public GetWebviewVersionResponder(Context context) {

        super(context);


    }


    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) {
        String webViewVersion = "0.0.0";
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packageInfoList = packageManager.getInstalledPackages(0);

        for (int i = 0; i < packageInfoList.size(); i++) {
            PackageInfo pak = packageInfoList.get(i);
            String applicationname = pak.applicationInfo.loadLabel(packageManager).toString();
            //判断是否为webview
            if(applicationname.equals("Android System WebView")){
                webViewVersion = pak.versionName;
                break;
            }

        }

        return Wire.Envelope.newBuilder()
            .setId(envelope.getId())
            .setType(Wire.MessageType.GET_WEBVIEW_VERSION)
            .setMessage(Wire.GetWebviewVersionResponse.newBuilder()
                .setSuccess(true).setVersion(webViewVersion)
                .build()
                .toByteString())
            .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }

}
