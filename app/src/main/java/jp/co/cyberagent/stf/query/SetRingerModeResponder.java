package jp.co.cyberagent.stf.query;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.media.AudioManager;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class SetRingerModeResponder extends AbstractResponder {
    public SetRingerModeResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.SetRingerModeRequest request =
                Wire.SetRingerModeRequest.parseFrom(envelope.getMessage());

        AudioManager am;
        am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        am.setRingerMode(request.getMode());

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.SET_RINGER_MODE)
                .setMessage(Wire.RemoveAccountResponse.newBuilder()
                        .setSuccess(true)
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
