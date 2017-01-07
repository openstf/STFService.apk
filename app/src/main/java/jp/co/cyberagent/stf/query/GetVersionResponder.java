package jp.co.cyberagent.stf.query;

import android.content.Context;

import com.google.protobuf.GeneratedMessageLite;

import jp.co.cyberagent.stf.BuildConfig;
import jp.co.cyberagent.stf.proto.Wire;

public class GetVersionResponder extends AbstractResponder {
    public GetVersionResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) {
        return Wire.GetVersionResponse.newBuilder()
                .setSuccess(true)
                .setVersion(BuildConfig.VERSION_NAME)
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
