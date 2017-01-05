package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.google.protobuf.GeneratedMessageLite;

import jp.co.cyberagent.stf.Version;
import jp.co.cyberagent.stf.proto.Wire;

public class GetVersionResponder extends AbstractResponder {
    public GetVersionResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) {
        return Wire.GetVersionResponse.newBuilder()
                .setSuccess(true)
                .setVersion(Version.name)
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
