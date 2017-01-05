package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.media.AudioManager;

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
        am.setStreamMute(AudioManager.STREAM_ALARM, state);
        am.setStreamMute(AudioManager.STREAM_DTMF, state);
        am.setStreamMute(AudioManager.STREAM_MUSIC, state);
        am.setStreamMute(AudioManager.STREAM_NOTIFICATION, state);
        am.setStreamMute(AudioManager.STREAM_RING, state);
        am.setStreamMute(AudioManager.STREAM_SYSTEM, state);
        am.setStreamMute(AudioManager.STREAM_VOICE_CALL, state);
    }
}
