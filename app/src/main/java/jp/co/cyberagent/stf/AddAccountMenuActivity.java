package jp.co.cyberagent.stf;

import android.accounts.AccountManager;
import android.app.Activity;
import android.os.Bundle;

public class AddAccountMenuActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AccountManager account = AccountManager.get(this);
        account.addAccount("com.google", null, null, null, this, null, null);
    }
}
