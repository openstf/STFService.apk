package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.telephony.TelephonyManager;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.util.NetworkUtil;

public class GetPropertiesResponder extends AbstractResponder {
    public GetPropertiesResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessage respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetPropertiesRequest request =
                Wire.GetPropertiesRequest.parseFrom(envelope.getMessage());

        ArrayList<Wire.Property> properties = new ArrayList<Wire.Property>();

        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        for (String name : request.getPropertiesList()) {
            if (name.equals("imei")) {
                String deviceId = tm.getDeviceId();
                if (deviceId != null && !deviceId.isEmpty()) {
                    properties.add(Wire.Property.newBuilder()
                            .setName(name)
                            .setValue(deviceId)
                            .build());
                }
            }
            else if (name.equals("phoneNumber")) {
                String phoneNumber = tm.getLine1Number();
                if (phoneNumber != null && !phoneNumber.isEmpty()) {
                    properties.add(Wire.Property.newBuilder()
                            .setName(name)
                            .setValue(phoneNumber)
                            .build());
                }
            }
            else if (name.equals("iccid")) {
                String iccid = tm.getSimSerialNumber();
                if (iccid != null && !iccid.isEmpty()) {
                    properties.add(Wire.Property.newBuilder()
                            .setName(name)
                            .setValue(iccid)
                            .build());
                }
            }
            else if (name.equals("operator")) {
                String operator;
                switch (tm.getPhoneType()) {
                    case TelephonyManager.PHONE_TYPE_CDMA:
                        operator = tm.getSimOperatorName();
                        break;
                    default:
                        operator = tm.getNetworkOperatorName();
                        if (operator == null || operator.isEmpty()) {
                            operator = tm.getSimOperatorName();
                        }
                }
                if (operator != null && !operator.isEmpty()) {
                    properties.add(Wire.Property.newBuilder()
                            .setName(name)
                            .setValue(operator)
                            .build());
                }
            }
            else if (name.equals("network")) {
                properties.add(Wire.Property.newBuilder()
                        .setName(name)
                        .setValue(NetworkUtil.getNetworkType(tm.getNetworkType()))
                        .build());
            }
        }

        return Wire.GetPropertiesResponse.newBuilder()
                .setSuccess(true)
                .addAllProperties(properties)
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
