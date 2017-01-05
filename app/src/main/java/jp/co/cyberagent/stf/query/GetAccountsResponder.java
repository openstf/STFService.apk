package jp.co.cyberagent.stf.query;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.util.Log;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import jp.co.cyberagent.stf.proto.Wire;

public class GetAccountsResponder extends AbstractResponder {
    public GetAccountsResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetAccountsRequest request =
                Wire.GetAccountsRequest.parseFrom(envelope.getMessage());

        String accountType = null;
        ArrayList<String> accountsName = new ArrayList<String>();
        AccountManager am = AccountManager.get(context);

        if(request.hasType()) {
            accountType = request.getType();
        }
        Account[] accounts = am.getAccountsByType(accountType);

        for (Account account : accounts) {
            accountsName.add(account.name);
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.GET_ACCOUNTS)
                .setMessage(Wire.GetAccountsResponse.newBuilder()
                        .setSuccess(true)
                        .addAllAccounts(accountsName)
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
