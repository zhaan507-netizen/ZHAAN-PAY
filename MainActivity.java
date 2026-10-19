package online.zhaan.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText urlBox, secretBox, sendersBox;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); ui.postDelayed(this, 1500); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad * 2, pad, pad);
        sv.addView(col);

        col.addView(label("Zhaan SMS Forwarder", 22));
        col.addView(label("Sends your bank / Telebirr SMS to the bot. If the phone is offline it keeps them and sends the moment the internet is back.", 14));

        col.addView(label("Server URL", 13));
        urlBox = field(Store.url(this), false);
        col.addView(urlBox);

        col.addView(label("Secret (the WEBHOOK_SECRET)", 13));
        secretBox = field(Store.secret(this), true);
        col.addView(secretBox);

        col.addView(label("Sender names to forward (comma separated)", 13));
        sendersBox = field(Store.senders(this), false);
        col.addView(sendersBox);

        col.addView(button("Save settings", v -> {
            Store.saveSettings(this, urlBox.getText().toString(), secretBox.getText().toString(), sendersBox.getText().toString());
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        }));
        col.addView(button("1. Allow SMS + notification permission", v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS}, 1);
            else if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 1);
        }));
        col.addView(button("2. Allow running in background (battery)", v -> askBattery()));
        col.addView(button("Send a test now", v -> {
            Store.saveSettings(this, urlBox.getText().toString(), secretBox.getText().toString(), sendersBox.getText().toString());
            Store.add(this, "Zhaan SMS Forwarder test");
            Flusher.schedule(this);
        }));
        col.addView(button("Send everything waiting now", v -> Flusher.schedule(this)));

        status = label("", 15);
        col.addView(status);
        setContentView(sv);
    }

    @Override protected void onResume() { super.onResume(); ForwardService.start(this); ui.post(tick); }
    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        ForwardService.start(this);
    }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(tick); }

    private void refresh() {
        boolean smsOk = Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        boolean batOk = true;
        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            batOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        status.setText("SMS permission: " + (smsOk ? "OK" : "NOT ALLOWED - tap button 1")
                + "\nBackground running: " + (batOk ? "OK" : "not allowed - tap button 2")
                + "\nWaiting to send: " + Store.pending(this)
                + "\nLast: " + Store.last(this));
    }

    private void askBattery() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private TextView label(String t, int sp) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(sp);
        v.setPadding(0, 16, 0, 4);
        return v;
    }

    private EditText field(String value, boolean secret) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        return e;
    }

    private Button button(String t, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setOnClickListener(l);
        return x;
    }
}
