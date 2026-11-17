package sy.homs.dash;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

public class Prefs {
    public static boolean on(Context c) { return c.getSharedPreferences("p", 0).getBoolean("adhan", true); }
    public static void set(Context c, boolean v) { c.getSharedPreferences("p", 0).edit().putBoolean("adhan", v).apply(); }
    public static String cfg(Context c) { return c.getSharedPreferences("p", 0).getString("cfg", "{}"); }
    public static void setCfg(Context c, String j) { c.getSharedPreferences("p", 0).edit().putString("cfg", j).apply(); }
    /** تنبيه «قد قامت الصلاة»: مفعّل افتراضياً، ويُغيَّر من إعدادات الشاشة */
    public static boolean iqOn(Context c) {
        try { return new JSONObject(cfg(c)).optInt("iqs", 1) != 0; } catch (Exception e) { return true; }
    }
    /** تشغيل التطبيق تلقائياً عند الإقلاع وعند تشغيل الشاشة: مفعّل افتراضياً */
    public static boolean auto(Context c) {
        try { return new JSONObject(cfg(c)).optInt("auto", 1) != 0; } catch (Exception e) { return true; }
    }
    /** مدة الإقامة بعد الأذان (دقائق): الفجر، الشروق، الظهر، العصر، المغرب، العشاء */
    public static int[] iq(Context c) {
        int[] r = {15, 0, 15, 15, 5, 15};
        try {
            JSONArray a = new JSONObject(cfg(c)).optJSONArray("iq");
            if (a != null && a.length() == 6) for (int i = 0; i < 6; i++) r[i] = Math.max(0, a.optInt(i, r[i]));
        } catch (Exception ignored) {}
        return r;
    }
    /** تعديل الدقائق لكل صلاة: الفجر، الشروق، الظهر، العصر، المغرب، العشاء */
    public static double[] adj(Context c) {
        double[] r = new double[6];
        try {
            JSONArray a = new JSONObject(cfg(c)).optJSONArray("adj");
            if (a != null) for (int i = 0; i < 6; i++) r[i] = a.optDouble(i, 0);
        } catch (Exception ignored) {}
        return r;
    }
}
