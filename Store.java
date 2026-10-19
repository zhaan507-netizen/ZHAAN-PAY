package online.zhaan.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings + the on-disk queue of SMS texts that have not been delivered to the server yet. */
final class Store {
    private static final String FILE = "zhaan_sms";
    static final String DEFAULT_URL = "http://162.254.32.27/notify";
    static final String DEFAULT_SENDERS = "127,CBE";

    private Store() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String url(Context c) { return sp(c).getString("url", DEFAULT_URL); }
    static String secret(Context c) { return sp(c).getString("secret", ""); }
    static String senders(Context c) { return sp(c).getString("senders", DEFAULT_SENDERS); }

    static void saveSettings(Context c, String url, String secret, String senders) {
        sp(c).edit().putString("url", url.trim()).putString("secret", secret.trim())
                .putString("senders", senders.trim()).apply();
    }

    static boolean senderAllowed(Context c, String sender) {
        if (sender == null) return false;
        String s = sender.trim();
        for (String part : senders(c).split(",")) {
            String p = part.trim();
            if (!p.isEmpty() && p.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    static void setLast(Context c, String status) {
        String when = new SimpleDateFormat("MMM d HH:mm:ss", Locale.US).format(new Date());
        sp(c).edit().putString("last", when + " - " + status).apply();
    }

    static String last(Context c) { return sp(c).getString("last", "nothing sent yet"); }

    // ----- queue -----
    static synchronized void add(Context c, String text) {
        try {
            JSONArray arr = new JSONArray(sp(c).getString("queue", "[]"));
            JSONObject o = new JSONObject();
            o.put("id", System.currentTimeMillis() + "-" + arr.length());
            o.put("text", text);
            arr.put(o);
            sp(c).edit().putString("queue", arr.toString()).commit();
        } catch (JSONException ignored) { }
    }

    static synchronized List<String[]> all(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("queue", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new String[]{o.getString("id"), o.getString("text")});
            }
        } catch (JSONException ignored) { }
        return out;
    }

    static synchronized void remove(Context c, String id) {
        try {
            JSONArray arr = new JSONArray(sp(c).getString("queue", "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!id.equals(o.getString("id"))) keep.put(o);
            }
            sp(c).edit().putString("queue", keep.toString()).commit();
        } catch (JSONException ignored) { }
    }

    static int pending(Context c) { return all(c).size(); }
}
