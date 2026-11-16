package sy.homs.dash;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.TimeZone;

public class Scheduler {
    public static final String ACT = "sy.homs.dash.ADHAN";
    static final double LAT = 34.7324, LNG = 36.7137, R = Math.PI / 180;
    static final int TZ = 3;
    static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    static int idx = 0;

    static String fmt(long ms) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(ms + TZ * 3600000L);
        int H = c.get(Calendar.HOUR_OF_DAY), M = c.get(Calendar.MINUTE);
        return (H % 12 == 0 ? 12 : H % 12) + ":" + (M < 10 ? "0" : "") + M + (H >= 12 ? " م" : " ص");
    }

    /** الفجر، الشروق، الظهر، العصر، المغرب، العشاء بالساعات (توقيت سوريا) */
    static double[] times(int y, int m, int d) {
        Calendar n = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        n.clear();
        n.set(y, m, d, 12, 0, 0);
        double D = n.getTimeInMillis() / 86400000.0 + 2440587.5 - 2451545, T = D / 36525;
        // حساب فلكي دقيق (NOAA) معايَر على جدول وزارة الأوقاف لحمص: فجر 18° وعشاء 17° وشروق/غروب 0.833° مع +1 د للظهر و+2 د للمغرب
        double L0 = ((280.46646 + 36000.76983 * T + .0003032 * T * T) % 360 + 360) % 360;
        double M = (357.52911 + 35999.05029 * T - .0001537 * T * T) * R;
        double C = (1.914602 - .004817 * T - .000014 * T * T) * Math.sin(M) + (.019993 - .000101 * T) * Math.sin(2 * M) + .000289 * Math.sin(3 * M);
        double om = (125.04 - 1934.136 * T) * R;
        double lam = (L0 + C - .00569 - .00478 * Math.sin(om)) * R;
        double ep = (23.439291 - .0130042 * T + .00256 * Math.cos(om)) * R;
        double dec = Math.asin(Math.sin(ep) * Math.sin(lam));
        double yy = Math.pow(Math.tan(ep / 2), 2), l0 = L0 * R, ec = .016708634 - .000042037 * T;
        double eq = 4 * (yy * Math.sin(2 * l0) - 2 * ec * Math.sin(M) + 4 * ec * yy * Math.sin(M) * Math.cos(2 * l0)
                - .5 * yy * yy * Math.sin(4 * l0) - 1.25 * ec * ec * Math.sin(2 * M)) / R / 60;
        double noon = 12 + TZ - LNG / 15 - eq;
        double asr = Math.atan(1 / (1 + Math.tan(Math.abs(LAT * R - dec)))) / R;
        return new double[]{noon - h(-18, dec), noon - h(-.833, dec), noon + 1.0 / 60, noon + h(asr, dec), noon + h(-.833, dec) + 2.0 / 60, noon + h(-17, dec)};
    }

    private static double h(double a, double dec) {
        return Math.acos((Math.sin(a * R) - Math.sin(LAT * R) * Math.sin(dec)) / (Math.cos(LAT * R) * Math.cos(dec))) / R / 15;
    }

    static long next(long now, double[] adj) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(now + TZ * 3600000L);
        for (int add = 0; add < 3; add++) {
            Calendar day = (Calendar) c.clone();
            day.add(Calendar.DAY_OF_MONTH, add);
            int y = day.get(Calendar.YEAR), m = day.get(Calendar.MONTH), d = day.get(Calendar.DAY_OF_MONTH);
            double[] t = times(y, m, d);
            Calendar mid = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            mid.clear();
            mid.set(y, m, d, 0, 0, 0);
            long base = mid.getTimeInMillis() - TZ * 3600000L;
            for (int i : new int[]{0, 2, 3, 4, 5}) {
                long tr = base + Math.round(t[i] * 60 + adj[i]) * 60000L; // تقريب لأقرب دقيقة كما في الشاشة
                if (tr > now + 20000) { idx = i; return tr; }
            }
        }
        return -1;
    }

    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long t = next(System.currentTimeMillis(), Prefs.adj(c));
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACT);
        i.putExtra("t", t);
        i.putExtra("i", idx);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!Prefs.on(c) || t < 0) {
            am.cancel(pi);
            c.getSharedPreferences("p", 0).edit().putLong("next", 0).apply();
            return;
        }
        c.getSharedPreferences("p", 0).edit().putLong("next", t).putString("nn", NAMES[idx]).apply();
        try {
            PendingIntent show = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), pi);
        } catch (Exception e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }
}
