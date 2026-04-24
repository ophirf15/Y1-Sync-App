package io.innoasis.y1syncer.util;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

public final class NetUtil {
    private NetUtil() {
    }

    public static String getWifiIp(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            return "0.0.0.0";
        }
        WifiInfo info = wifiManager.getConnectionInfo();
        if (info == null) {
            return "0.0.0.0";
        }
        return Formatter.formatIpAddress(info.getIpAddress());
    }
}
