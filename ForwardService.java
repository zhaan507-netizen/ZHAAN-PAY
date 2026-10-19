package online.zhaan.smsforwarder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps a permanent notification in the notification bar (like MacroDroid does). While it is there Android treats
 * the app as running, so it is not put to sleep. It also listens for "internet is back" and sends the queue at once.
 */
public class ForwardService extends Service {
    private static final String CHANNEL = "zhaan_forwarder";
    private static final int NOTE_ID = 1;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            try {
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTE_ID, build());
            } catch (Exception ignored) { }
            ui.postDelayed(this, 3000);
        }
    };

    /** Start (or keep) the permanent notification. Safe to call many times. */
    static void start(Context c) {
        try {
            Intent i = new Intent(c.getApplicationContext(), ForwardService.class);
            if (Build.VERSION.SDK_INT >= 26) c.getApplicationContext().startForegroundService(i);
            else c.getApplicationContext().startService(i);
        } catch (Exception ignored) { /* Android may refuse a background start; it starts the next time the app is opened */ }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "SMS forwarder status", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Notification n = build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTE_ID, n);

        // The moment the internet comes back: send everything that is waiting.
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { flush(); }
        };
        try { cm.registerDefaultNetworkCallback(callback); } catch (Exception ignored) { }
        ui.postDelayed(refresh, 3000);
        flush();
    }

    private void flush() {
        final Context app = getApplicationContext();
        worker.execute(() -> {
            if (Store.pending(app) > 0 && !FlushWorker.flushNow(app)) Flusher.schedule(app);
        });
    }

    private Notification build() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Zhaan SMS Forwarder is running")
                .setContentText("Waiting: " + Store.pending(this) + "  |  " + Store.last(this))
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        return b.build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        ui.removeCallbacks(refresh);
        try { if (cm != null && callback != null) cm.unregisterNetworkCallback(callback); } catch (Exception ignored) { }
        worker.shutdown();
        super.onDestroy();
    }
}
