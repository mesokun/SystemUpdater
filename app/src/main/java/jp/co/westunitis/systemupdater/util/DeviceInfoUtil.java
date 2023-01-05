package jp.co.westunitis.systemupdater.util;

import android.os.Build;

public class DeviceInfoUtil {
    private static final String TAG = "DeviceInfoUtil";
    private static final String UNKNOWN = "Unknown";
    private static final String MODEL_INFO_LINKER = "InfoLinker3";
    private static final String MODEL_WL03A = "WL-03A";

    public static String getSerialNumber() {
        try {
            return Build.getSerial();
        } catch (SecurityException e) {
            return UNKNOWN;
        }
    }

    public static boolean isInfoLinker() {
        LogUtil.d(TAG, "Build.MODEL:" + Build.MODEL);
        return (Build.MODEL.equals(MODEL_INFO_LINKER) || Build.MODEL.equals(MODEL_WL03A));
    }
}
