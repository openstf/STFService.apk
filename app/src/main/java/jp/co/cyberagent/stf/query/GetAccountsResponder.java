package jp.co.cyberagent.stf.query;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import jp.co.cyberagent.stf.proto.Wire;

public class GetAccountsResponder extends AbstractResponder {
    public GetAccountsResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetAccountsRequest request =
                Wire.GetAccountsRequest.parseFrom(envelope.getMessage());

        AccountManager am = AccountManager.get(context);
        Account[] accounts = am.getAccountsByType(request.getType());
        boolean successResult = false;
        ArrayList<String> accountsName = new ArrayList<String>();

        if (accounts.length > 0){
            successResult = true;
            for (Account account : accounts) {
                accountsName.add(account.name);
            }
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.GET_ACCOUNTS)
                .setMessage(Wire.GetAccountsResponse.newBuilder()
                        .setSuccess(successResult)
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
