package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.os.Build;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class GetBluetoothStatusResponder extends AbstractResponder {
    public GetBluetoothStatusResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetBluetoothStatusRequest request =
                Wire.GetBluetoothStatusRequest.parseFrom(envelope.getMessage());

        Wire.GetBluetoothStatusResponse.Builder builder = Wire.GetBluetoothStatusResponse.newBuilder();

        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            BluetoothManager bm = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bm != null) {
                BluetoothAdapter ba = bm.getAdapter();
                if(ba != null) {
                    builder.setStatus(ba.isEnabled());
                    builder.setSuccess(true);
                }
                // No Bluetooth available
                else {
                    builder.setSuccess(false);
                }
            }
            // No Bluetooth available
            else {
                builder.setSuccess(false);
            }
        }
        // getAdapter() is only available since Android API level 18
        else {
            builder.setSuccess(false);
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.GET_BLUETOOTH_STATUS)
                .setMessage(builder
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
