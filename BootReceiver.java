package online.zhaan.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ForwardService.start(context);
        if (Store.pending(context) > 0) Flusher.schedule(context);
    }
}
