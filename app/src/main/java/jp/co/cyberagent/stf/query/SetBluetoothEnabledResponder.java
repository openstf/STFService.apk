package jp.co.cyberagent.stf.query;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.bluetooth.BluetoothManager;
import android.os.Build;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class SetBluetoothEnabledResponder extends AbstractResponder {
    public SetBluetoothEnabledResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.SetBluetoothEnabledRequest request =
                Wire.SetBluetoothEnabledRequest.parseFrom(envelope.getMessage());

        boolean successful;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            BluetoothManager bm = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bm != null) {
                BluetoothAdapter ba = bm.getAdapter();
                if (ba != null) {
                    if (request.getEnabled()) {
                        ba.enable();
                    }
                    else {
                        ba.disable();
                    }
                    successful = true;
                }
                // No Bluetooth available
                else {
                    successful = false;
                }
            }
            // No Bluetooth available
            else {
                successful = false;
            }
        }
        // getAdapter() is only available since Android API level 18
        else {
            successful = false;
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.SET_BLUETOOTH_ENABLED)
                .setMessage(Wire.SetBluetoothEnabledResponse.newBuilder()
                        .setSuccess(successful)
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
