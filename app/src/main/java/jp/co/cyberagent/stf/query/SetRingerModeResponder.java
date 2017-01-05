package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.media.AudioManager;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class SetRingerModeResponder extends AbstractResponder {
    public SetRingerModeResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.SetRingerModeRequest request =
                Wire.SetRingerModeRequest.parseFrom(envelope.getMessage());

        AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        switch (request.getMode()) {
            case SILENT:
                am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                break;
            case VIBRATE:
                am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                break;
            case NORMAL:
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                break;
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.SET_RINGER_MODE)
                .setMessage(Wire.SetRingerModeResponse.newBuilder()
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
