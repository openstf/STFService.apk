package jp.co.cyberagent.stf.query;

import android.content.ClipData;
import android.content.Context;
import android.os.Build;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.Service;
import jp.co.cyberagent.stf.proto.Wire;

public class GetClipboardResponder extends AbstractResponder {
    public GetClipboardResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetClipboardRequest request =
                Wire.GetClipboardRequest.parseFrom(envelope.getMessage());

        switch (request.getType()) {
            case TEXT:
                CharSequence text = getClipboardText();

                if (text == null) {
                    return Wire.Envelope.newBuilder()
                        .setId(envelope.getId())
                        .setType(Wire.MessageType.GET_CLIPBOARD)
                        .setMessage(Wire.GetClipboardResponse.newBuilder()
                                .setSuccess(true)
                                .setType(Wire.ClipboardType.TEXT)
                                .build()
                                .toByteString())
                        .build();
                }

                return Wire.Envelope.newBuilder()
                        .setId(envelope.getId())
                        .setType(Wire.MessageType.GET_CLIPBOARD)
                        .setMessage(Wire.GetClipboardResponse.newBuilder()
                                .setSuccess(true)
                                .setType(Wire.ClipboardType.TEXT)
                                .setText(text.toString())
                                .build()
                                .toByteString())
                        .build();
            default:
                return Wire.Envelope.newBuilder()
                        .setId(envelope.getId())
                        .setType(Wire.MessageType.GET_CLIPBOARD)
                        .setMessage(Wire.GetClipboardResponse.newBuilder()
                            .setSuccess(false)
                            .build()
                            .toByteString())
                        .build();
        }
    }

    @Override
    public void cleanup() {
        // No-op
    }

    private CharSequence getClipboardText() {
        if (Build.VERSION.SDK_INT >= 11) {
            android.content.ClipboardManager clipboardManager =
                    (android.content.ClipboardManager) Service.getClipboardManager();
            if (clipboardManager.hasPrimaryClip()) {
                ClipData clipData = clipboardManager.getPrimaryClip();
                if (clipData.getItemCount() > 0) {
                    ClipData.Item clip = clipData.getItemAt(0);
                    return clip.coerceToText(context.getApplicationContext());
                }
                else {
                    return null;
                }
            }
            else {
                return null;
            }
        }
        else {
            return ((android.text.ClipboardManager) Service.getClipboardManager())
                    .getText();
        }
    }
}
