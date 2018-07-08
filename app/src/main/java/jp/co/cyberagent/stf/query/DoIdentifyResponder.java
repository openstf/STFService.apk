package jp.co.cyberagent.stf.query;

import android.content.Context;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.IdentityActivity;
import jp.co.cyberagent.stf.proto.Wire;

public class DoIdentifyResponder extends AbstractResponder {
    public DoIdentifyResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.DoIdentifyRequest request =
                Wire.DoIdentifyRequest.parseFrom(envelope.getMessage());

        showIdentity(request.getSerial());

        return Wire.Envelope.newBuilder()
            .setId(envelope.getId())
            .setType(Wire.MessageType.DO_IDENTIFY)
            .setMessage(Wire.DoIdentifyResponse.newBuilder()
                .setSuccess(true)
                .build()
                .toByteString())
            .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }

    private void showIdentity(String serial) {
        context.startActivity(new IdentityActivity.IntentBuilder()
            .serial(serial)
            .build(context));
    }
}
