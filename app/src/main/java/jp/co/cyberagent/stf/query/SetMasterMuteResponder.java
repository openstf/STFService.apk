package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class SetMasterMuteResponder extends AbstractResponder {
    public SetMasterMuteResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.SetMasterMuteRequest request =
                Wire.SetMasterMuteRequest.parseFrom(envelope.getMessage());

        mute(request.getEnabled());

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.SET_MASTER_MUTE)
                .setMessage(Wire.SetMasterMuteResponse.newBuilder()
                        .setSuccess(true)
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        mute(false);
    }

    private void mute(boolean state) {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= 23) {
            int direction = state ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE;
            int flags = 0;
            am.adjustStreamVolume(AudioManager.STREAM_ALARM, direction, flags);
            am.adjustStreamVolume(AudioManager.STREAM_DTMF, direction, flags);
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags);
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, direction, flags);
            am.adjustStreamVolume(AudioManager.STREAM_RING, direction, flags);
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, direction, flags);
            am.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, direction, flags);
        } else {
            am.setStreamMute(AudioManager.STREAM_ALARM, state);
            am.setStreamMute(AudioManager.STREAM_DTMF, state);
            am.setStreamMute(AudioManager.STREAM_MUSIC, state);
            am.setStreamMute(AudioManager.STREAM_NOTIFICATION, state);
            am.setStreamMute(AudioManager.STREAM_RING, state);
            am.setStreamMute(AudioManager.STREAM_SYSTEM, state);
            am.setStreamMute(AudioManager.STREAM_VOICE_CALL, state);
        }
    }
}
