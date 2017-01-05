package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import jp.co.cyberagent.stf.proto.Wire;

public class GetDisplayResponder extends AbstractResponder {
    public GetDisplayResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {
        Wire.GetDisplayRequest request =
                Wire.GetDisplayRequest.parseFrom(envelope.getMessage());

        if (Build.VERSION.SDK_INT >= 17) {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);

            Display display = dm.getDisplay(request.getId());

            DisplayMetrics real = new DisplayMetrics();
            display.getRealMetrics(real);

            // DisplayMetrics is adjusted for rotation, so we have to swap it back if
            // necessary.
            int rotation = display.getRotation();

            if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
            {
                return Wire.Envelope.newBuilder()
                        .setId(envelope.getId())
                        .setType(Wire.MessageType.GET_DISPLAY)
                        .setMessage(Wire.GetDisplayResponse.newBuilder()
                                .setSuccess(true)
                                .setWidth(real.heightPixels)
                                .setHeight(real.widthPixels)
                                .setXdpi(real.ydpi)
                                .setYdpi(real.xdpi)
                                .setFps(display.getRefreshRate())
                                .setDensity(real.density)
                                .setRotation(rotationToDegrees(rotation))
                                .setSecure((display.getFlags() & Display.FLAG_SECURE) == Display.FLAG_SECURE)
                                .build()
                                .toByteString())
                        .build();
            }
            else {
                return Wire.Envelope.newBuilder()
                        .setId(envelope.getId())
                        .setType(Wire.MessageType.GET_DISPLAY)
                        .setMessage(Wire.GetDisplayResponse.newBuilder()
                                .setSuccess(true)
                                .setWidth(real.widthPixels)
                                .setHeight(real.heightPixels)
                                .setXdpi(real.xdpi)
                                .setYdpi(real.ydpi)
                                .setFps(display.getRefreshRate())
                                .setDensity(real.density)
                                .setRotation(rotationToDegrees(rotation))
                                .setSecure((display.getFlags() & Display.FLAG_SECURE) == Display.FLAG_SECURE)
                                .build()
                                .toByteString())
                        .build();
            }
        }
        else {
            return Wire.Envelope.newBuilder()
                    .setId(envelope.getId())
                    .setType(Wire.MessageType.GET_DISPLAY)
                    .setMessage(Wire.GetDisplayResponse.newBuilder()
                            .setSuccess(false)
                            .build()
                            .toByteString())
                    .build();
        }
    }

    private int rotationToDegrees(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return -1;
        }
    }

    @Override
    public void cleanup() {
        // No-op
    }
}
