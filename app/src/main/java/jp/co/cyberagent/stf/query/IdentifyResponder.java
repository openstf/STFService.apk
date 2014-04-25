package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.content.Intent;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.IdentityActivity;
import jp.co.cyberagent.stf.Wire;

public class IdentifyResponder extends AbstractResponder {
    public IdentifyResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.RequestEnvelope envelope) throws InvalidProtocolBufferException {
        Wire.IdentifyRequest request =
                Wire.IdentifyRequest.parseFrom(envelope.getRequest());

        showIdentity(request.getSerial());

        return Wire.IdentifyResponse.newBuilder()
                .setSuccess(true)
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }

    private void showIdentity(String serial) {
        Intent intent = new Intent(context, IdentityActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(IdentityActivity.EXTRA_SERIAL, serial);
        context.startActivity(intent);
    }
}
