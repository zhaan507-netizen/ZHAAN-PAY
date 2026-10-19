package online.zhaan.smsforwarder;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Schedules "send everything in the queue". The job has a "network connected" condition, so Android runs it
 * by itself the moment the phone is back online - no polling, no loop.
 */
final class Flusher {
    private Flusher() {}

    static void schedule(Context c) {
        Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(FlushWorker.class)
                .setConstraints(net)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(c.getApplicationContext())
                .enqueueUniqueWork("flush", ExistingWorkPolicy.REPLACE, req);
    }
}
