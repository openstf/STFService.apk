package jp.co.cyberagent.stf.query;

import android.content.ClipData;
import android.content.Context;
import android.os.Build;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.Wire;

public class GetClipboardResponder extends AbstractResponder {
    public GetClipboardResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.RequestEnvelope envelope) throws InvalidProtocolBufferException {
        Wire.GetClipboardRequest request =
                Wire.GetClipboardRequest.parseFrom(envelope.getRequest());

        switch (request.getType()) {
            case TEXT:
                CharSequence text = getClipboardText();

                if (text == null) {
                    return Wire.GetClipboardResponse.newBuilder()
                            .setSuccess(true)
                            .build();
                }

                return Wire.GetClipboardResponse.newBuilder()
                        .setSuccess(true)
                        .setText(text.toString())
                        .build();
            default:
                return Wire.GetClipboardResponse.newBuilder()
                        .setSuccess(false)
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
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
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
            android.text.ClipboardManager clipboardManager =
                    (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            return clipboardManager.getText();
        }
    }
}
