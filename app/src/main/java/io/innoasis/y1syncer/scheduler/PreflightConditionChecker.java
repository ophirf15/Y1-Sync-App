package io.innoasis.y1syncer.scheduler;

import android.content.Context;

public class PreflightConditionChecker {
    public boolean canRunSync(Context context) {
        // Stage 1 simplified preflight: always true.
        // Hooks for SSID, charging, and battery-threshold checks are intentionally centralized here.
        return true;
    }
}
