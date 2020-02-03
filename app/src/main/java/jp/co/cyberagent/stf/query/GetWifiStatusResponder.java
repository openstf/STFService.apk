package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class GetWifiStatusResponder extends AbstractResponder {
    public GetWifiStatusResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetWifiStatusRequest request =
                Wire.GetWifiStatusRequest.parseFrom(envelope.getMessage());

        WifiManager wm = (WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.GET_WIFI_STATUS)
                .setMessage(Wire.GetWifiStatusResponse.newBuilder()
                        .setSuccess(true)
                        .setStatus(wm.isWifiEnabled())
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
