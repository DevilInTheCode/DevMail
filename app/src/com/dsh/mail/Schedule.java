package com.dsh.mail;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import java.util.List;

public final class Schedule {

    public static final int JOB_ID = 1001;

    private Schedule() {
    }

    /** Периодическая проверка почты в фоне (минимум Android — 15 минут). */
    public static void reschedule(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            js.cancel(JOB_ID);
            Db db = new Db(ctx);
            List<Account> accs = db.accounts();
            int min = Integer.MAX_VALUE;
            for (Account a : accs) {
                if (a.checkMin > 0) min = Math.min(min, a.checkMin);
            }
            if (min == Integer.MAX_VALUE) return;
            int period = Math.max(15, min);
            JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, SyncJobService.class))
                    .setPersisted(true)
                    .setPeriodic(period * 60000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build();
            js.schedule(job);
        } catch (Exception ignored) {
        }
    }
}
