package io.innoasis.y1syncer.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class SyncAlarmScheduler {
    public static final String ACTION_INTERVAL_SYNC = "io.innoasis.y1syncer.ACTION_INTERVAL_SYNC";
    private final Context context;

    public SyncAlarmScheduler(Context context) {
        this.context = context.getApplicationContext();
    }

    public void scheduleInterval(long intervalMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = buildIntent();
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + intervalMs, intervalMs, pi);
    }

    public void cancel() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.cancel(buildIntent());
    }

    private PendingIntent buildIntent() {
        Intent i = new Intent(context, ScheduleReceiver.class);
        i.setAction(ACTION_INTERVAL_SYNC);
        return PendingIntent.getBroadcast(context, 101, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
