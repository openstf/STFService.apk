package jp.co.cyberagent.stf.query;

import android.content.ClipData;
import android.content.Context;
import android.os.Build;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class SetClipboardResponder extends AbstractResponder {
    public SetClipboardResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.SetClipboardRequest request =
                Wire.SetClipboardRequest.parseFrom(envelope.getMessage());

        switch (request.getType()) {
            case TEXT:
                setClipboardText(request.getText());
                return Wire.Envelope.newBuilder()
                        .setId(envelope.getId())
                        .setType(Wire.MessageType.SET_CLIPBOARD)
                            .setMessage(Wire.SetClipboardResponse.newBuilder()
                            .setSuccess(true)
                            .build()
                            .toByteString())
                        .build();
            default:
                return Wire.Envelope.newBuilder()
                        .setId(envelope.getId())
                        .setType(Wire.MessageType.SET_CLIPBOARD)
                        .setMessage(Wire.SetClipboardResponse.newBuilder()
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

    private void setClipboardText(String content) {
        if (Build.VERSION.SDK_INT >= 11) {
            android.content.ClipboardManager clipboardManager =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, content));
        }
        else {
            android.text.ClipboardManager clipboardManager =
                    (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboardManager.setText(content);
        }
    }
}
