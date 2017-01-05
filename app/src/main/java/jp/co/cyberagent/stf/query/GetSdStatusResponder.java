package jp.co.cyberagent.stf.query;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import jp.co.cyberagent.stf.proto.Wire;
import jp.co.cyberagent.stf.util.BrowserUtil;
import jp.co.cyberagent.stf.util.NetworkUtil;

public class GetSdStatusResponder extends AbstractResponder {
    public GetSdStatusResponder(Context context) {
        super(context);
    }

    @Override
    public GeneratedMessageLite respond(Wire.Envelope envelope) throws InvalidProtocolBufferException {

        boolean sd_card_mounted = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            List<String> mountList = new ArrayList<String>();
            String mount_sdcard = null;

            Scanner scanner = null;
            try {
                File vold_fstab = new File("/system/etc/vold.fstab");
                scanner = new Scanner(new FileInputStream(vold_fstab));
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("dev_mount") || line.startsWith("fuse_mount")) {
                        String path = line.replaceAll("\t", " ").split(" ")[2];
                        if (!mountList.contains(path)) {
                            mountList.add(path);
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                if (!Environment.isExternalStorageRemovable()) {
                    mountList.remove(Environment.getExternalStorageDirectory().getPath());
                }
            }

            for (int i = 0; i < mountList.size(); i++) {
                if (!isMounted(mountList.get(i))) {
                    mountList.remove(i--);
                }
            }

            if (mountList.size() > 0) {
                sd_card_mounted = true;
            }
        }
        else {
            String path = System.getenv( "SECONDARY_STORAGE" );
            if(path != null && isMounted(path)){
                sd_card_mounted = true;
            }
        }

        return Wire.Envelope.newBuilder()
                .setId(envelope.getId())
                .setType(Wire.MessageType.GET_SD_STATUS)
                .setMessage(Wire.GetSdStatusResponse.newBuilder()
                        .setSuccess(true)
                        .setMounted(sd_card_mounted)
                        .build()
                        .toByteString())
                .build();
    }

    @Override
    public void cleanup() {
        // No-op
    }

    private boolean isMounted(String path) {
        boolean isMounted = false;

        Scanner scanner = null;
        try {
            File mounts = new File("/proc/mounts");
            scanner = new Scanner(new FileInputStream(mounts));
            while (scanner.hasNextLine()) {
                if (scanner.nextLine().contains(path)) {
                    isMounted = true;
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }

        return isMounted;
    }
}

