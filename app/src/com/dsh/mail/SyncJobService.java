package com.dsh.mail;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import java.util.List;

public class SyncJobService extends JobService {

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            Db db = new Db(SyncJobService.this);
            int added = 0;
            try {
                List<Account> accs = db.accounts();
                for (Account a : accs) {
                    if (a.checkMin <= 0) continue;
                    try {
                        added += MailService.syncAccount(SyncJobService.this, db, a).added;
                    } catch (Exception e) {
                        Log.w("Mail", "job sync " + a.email + ": " + e);
                    }
                }
            } catch (Exception e) {
                Log.w("Mail", "job: " + e);
            }
            if (added > 0) Notifier.newMail(SyncJobService.this);
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
