package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.google.protobuf.GeneratedMessage;

import jp.co.cyberagent.stf.proto.Wire;

public class GetVersionResponder extends AbstractResponder {
    public GetVersionResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.RequestEnvelope envelope) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            return Wire.GetVersionResponse.newBuilder()
                    .setSuccess(true)
                    .setVersion(info.versionName)
                    .build();
        }
        catch (PackageManager.NameNotFoundException e) {
            return Wire.GetVersionResponse.newBuilder()
                    .setSuccess(false)
                    .build();
        }
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
