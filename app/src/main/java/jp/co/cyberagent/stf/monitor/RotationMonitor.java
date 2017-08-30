package jp.co.cyberagent.stf.monitor;

import android.content.Context;
import android.util.Log;
import android.view.Surface;

import jp.co.cyberagent.stf.io.MessageWritable;
import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.compat.WindowManagerWrapper;

public class RotationMonitor extends AbstractMonitor {
    private static final String TAG = "STFRotationMonitor";

    private WindowManagerWrapper wmw = new WindowManagerWrapper();

    public RotationMonitor(Context context, MessageWritable writer) {
        super(context, writer);
    }

    @Override
    public void run() {
        Log.i(TAG, "Monitor starting");

        WindowManagerWrapper.RotationWatcher watcher = new WindowManagerWrapper.RotationWatcher() {
            @Override
            public void onRotationChanged(int rotation) {
                report(writer, rotation);
            }
        };

        try {
            Object nativeWatcher = wmw.watchRotation(watcher);

            synchronized (this) {
                while (!isInterrupted()) {
                    wait();
                }
            }
        }
        catch (InterruptedException e) {
            // Okay
        }
        finally {
            Log.i(TAG, "Monitor stopping");

            // Sadly, removeRotationWatcher is only available on API >= 18. Instead, we
            // must make sure that whole process dies, causing DeathRecipient to reap the
            // watcher.
        }
    }

    @Override
    public void peek(MessageWritable writer) {
        report(writer, wmw.getRotation());
    }

    private void report(MessageWritable writer, int rotation) {
        Log.i(TAG, String.format("Rotation is %d", rotation));

        writer.write(Wire.Envelope.newBuilder()
            .setType(Wire.MessageType.EVENT_ROTATION)
            .setMessage(Wire.RotationEvent.newBuilder()
                .setRotation(rotationToDegrees(rotation))
                .build()
                .toByteString())
            .build());
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
                return 0;
        }
    }
}
