package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.content.Intent;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.AddAccountMenuActivity;
import jp.co.cyberagent.stf.proto.Wire;

public class DoAddAccountMenuResponder extends AbstractResponder {
    public DoAddAccountMenuResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.RemoveAccountRequest request =
                Wire.RemoveAccountRequest.parseFrom(envelope.getMessage());

        showAddAccountMenu();

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.DO_ADD_ACCOUNT_MENU)
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

    private void showAddAccountMenu () {
        Intent intent = new Intent(context, AddAccountMenuActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
