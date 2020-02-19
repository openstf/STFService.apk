package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;

import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.util.NetworkUtil;

public class GetPropertiesResponder extends AbstractResponder {
    private static final String TAG = GetPropertiesResponder.class.getSimpleName();

    public GetPropertiesResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetPropertiesRequest request =
                Wire.GetPropertiesRequest.parseFrom(envelope.getMessage());

        ArrayList<Wire.Property> properties = new ArrayList<Wire.Property>();
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        for (String name : request.getPropertiesList()) {
            String value = null;
            try {
                switch (name) {
                    case "imei":
                        value = getValueOrNull(tm::getDeviceId);
                        break;
                    case "imsi":
                        value = getValueOrNull(tm::getSubscriberId);
                        break;
                    case "phoneNumber":
                        value = tm.getLine1Number();
                        break;
                    case "iccid":
                        value = getValueOrNull(tm::getSimSerialNumber);
                        break;
                    case "operator":
                        if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                            value = tm.getSimOperatorName();
                        } else {
                            value = tm.getNetworkOperatorName();
                            if (TextUtils.isEmpty(value)) {
                                value = tm.getSimOperatorName();
                            }
                        }
                        break;
                    case "network":
                        value = NetworkUtil.getNetworkType(tm.getNetworkType());
                        break;
                    default:
                        Log.d(TAG, "unknown property request");

                }
                if (!TextUtils.isEmpty(value)) {
                    properties.add(Wire.Property.newBuilder()
                            .setName(name)
                            .setValue(value)
                            .build());
                }
            } catch (SecurityException e) {
                Log.d(TAG, "Security exception trying to retrieve " + name);
            }
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.GET_PROPERTIES)
                .setMessage(Wire.GetPropertiesResponse.newBuilder()
                        .setSuccess(true)
                        .addAllProperties(properties)
                        .build()
                        .toByteString())
                .build();
    }

    private static String getValueOrNull(StringCallable callable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return null;
        }
        return callable.call();
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
