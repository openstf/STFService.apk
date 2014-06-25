package jp.co.cyberagent.stf.query;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class RemoveAccountResponder extends AbstractResponder {
    public RemoveAccountResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.RemoveAccountRequest request =
                Wire.RemoveAccountRequest.parseFrom(envelope.getMessage());

        AccountManager am = AccountManager.get(context);

        Account[] accounts = am.getAccountsByType("com.google");
        for (Account account : accounts) {
            am.removeAccount(account, null, null);
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.REMOVE_ACCOUNT)
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
