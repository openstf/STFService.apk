package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.media.AudioManager;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class GetRingerModeResponder extends AbstractResponder {
    public GetRingerModeResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {

        AudioManager am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        Wire.RingerMode ringerMode = null;

        switch (am.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                ringerMode = Wire.RingerMode.SILENT;
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                ringerMode = Wire.RingerMode.VIBRATE;
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                ringerMode = Wire.RingerMode.NORMAL;
                break;
        }

        Wire.GetRingerModeResponse.Builder message = Wire.GetRingerModeResponse.newBuilder();
        if(ringerMode != null) {
            message.setSuccess(true).setMode(ringerMode);
        }
        else {
            message.setSuccess(false);
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.GET_RINGER_MODE)
                .setMessage(message.build().toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
