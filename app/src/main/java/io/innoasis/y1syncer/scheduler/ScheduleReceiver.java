package io.innoasis.y1syncer.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.innoasis.y1syncer.app.RuntimeApplication;

public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        RuntimeApplication app = (RuntimeApplication) context.getApplicationContext();
        if (new PreflightConditionChecker().canRunSync(context)) {
            app.getRuntimeController().syncNow("scheduled");
        }
    }
}
