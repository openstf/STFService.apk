package jp.co.cyberagent.stf.query;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;

import jp.co.cyberagent.stf.proto.Wire;

public class DoAddAccountMenuResponder extends AbstractResponder {
    public DoAddAccountMenuResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.DoAddAccountMenuRequest request =
                Wire.DoAddAccountMenuRequest.parseFrom(envelope.getMessage());

        try {
            showAddAccountMenu();
        } catch (AuthenticatorException e) {
            e.printStackTrace();
        } catch (OperationCanceledException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.DO_ADD_ACCOUNT_MENU)
                .setMessage(Wire.DoAddAccountMenuResponse.newBuilder()
                        .setSuccess(true)
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }

    private void showAddAccountMenu() throws AuthenticatorException, OperationCanceledException, IOException {
        AccountManager account = AccountManager.get(context);
        AccountManagerFuture<Bundle> accountFuture = account.addAccount("com.google", null, null, null, null, null, null);
        Bundle bundle = accountFuture.getResult();
        Intent intent = (Intent)bundle.get(AccountManager.KEY_INTENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
