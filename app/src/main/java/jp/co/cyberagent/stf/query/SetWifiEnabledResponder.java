package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class SetWifiEnabledResponder extends AbstractResponder {
    public SetWifiEnabledResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.SetWifiEnabledRequest request =
                Wire.SetWifiEnabledRequest.parseFrom(envelope.getMessage());

        WifiManager wm = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);

        wm.setWifiEnabled(request.getEnabled());

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.SET_WIFI_ENABLED)
                .setMessage(Wire.SetWifiEnabledResponse.newBuilder()
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
