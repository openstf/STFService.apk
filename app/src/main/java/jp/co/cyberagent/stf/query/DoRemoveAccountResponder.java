package jp.co.cyberagent.stf.query;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;

import jp.co.cyberagent.stf.proto.Wire;

public class DoRemoveAccountResponder extends AbstractResponder {
    public DoRemoveAccountResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.DoRemoveAccountRequest request =
                Wire.DoRemoveAccountRequest.parseFrom(envelope.getMessage());

        AccountManager am = AccountManager.get(context);
        Account[] accounts = am.getAccountsByType(request.getType());
        AccountManagerFuture<Boolean> success = null;
        boolean successResult = false;

        if(request.hasAccount()) {
            for (Account account : accounts) {
                if(account.name.equals(request.getAccount())) {
                    success = am.removeAccount(account, null, null);
                }
            }
        }
        else {
            for (Account account : accounts) {
                success = am.removeAccount(account, null, null);
            }
        }

        if(success != null) {
            try {
                successResult = success.getResult();
            } catch (OperationCanceledException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AuthenticatorException e) {
                e.printStackTrace();
            }
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.DO_REMOVE_ACCOUNT)
                .setMessage(Wire.DoRemoveAccountResponse.newBuilder()
                        .setSuccess(successResult)
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
