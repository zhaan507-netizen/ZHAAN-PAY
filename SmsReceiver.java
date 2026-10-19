package online.zhaan.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;

        // A long SMS arrives in several parts from the same sender - join them back into one text.
        Map<String, StringBuilder> bySender = new LinkedHashMap<>();
        for (SmsMessage m : parts) {
            if (m == null) continue;
            String from = m.getOriginatingAddress() == null ? "" : m.getOriginatingAddress();
            StringBuilder sb = bySender.get(from);
            if (sb == null) { sb = new StringBuilder(); bySender.put(from, sb); }
            sb.append(m.getMessageBody());
        }

        boolean queued = false;
        for (Map.Entry<String, StringBuilder> e : bySender.entrySet()) {
            if (Store.senderAllowed(context, e.getKey())) {
                Store.add(context, e.getValue().toString());
                queued = true;
            }
        }
        if (queued) {
            Store.setLast(context, "SMS received, sending...");
            final Context app = context.getApplicationContext();
            final PendingResult pending = goAsync();
            new Thread(() -> {
                try {
                    // Try right now. If the phone is offline or the server is unreachable, the queued job below
                    // sends it by itself as soon as the internet is back.
                    if (!FlushWorker.flushNow(app)) Flusher.schedule(app);
                } finally {
                    pending.finish();
                }
            }).start();
            Flusher.schedule(app); // safety net: also runs when the network connects
        }
    }
}
