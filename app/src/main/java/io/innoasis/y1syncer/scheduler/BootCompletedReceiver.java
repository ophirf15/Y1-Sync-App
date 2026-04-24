package io.innoasis.y1syncer.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            new SyncAlarmScheduler(context).scheduleInterval(6 * 60 * 60 * 1000L);
        }
    }
}
