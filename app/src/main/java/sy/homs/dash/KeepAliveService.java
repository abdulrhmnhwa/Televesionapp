package sy.homs.dash;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** خدمة أمامية خفيفة تبقي التطبيق حيّاً بالخلفية ليعمل الأذان في وقته */
public class KeepAliveService extends Service {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { check(); h.postDelayed(this, 15000); }
    };

    /** يفتح الشاشة الرئيسية إن لم تكن ظاهرة (عند الإقلاع أو تشغيل الشاشة) */
    static void launchUI(Context c) {
        if (MainActivity.fg || !Prefs.auto(c)) return;
        try {
            c.startActivity(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (Throwable ignored) {}
    }

    private BroadcastReceiver scr;

    static void start(Context c) { start(c, false); }

    static void start(Context c, boolean boot) {
        try {
            Intent i = new Intent(c, KeepAliveService.class).putExtra("boot", boot);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel("keep", "تشغيل الأذان بالخلفية", NotificationManager.IMPORTANCE_MIN));
                b = new Notification.Builder(this, "keep");
            } else {
                b = new Notification.Builder(this);
            }
            b.setSmallIcon(R.drawable.ic_stat).setContentTitle("لوحة حمص تعمل بالخلفية")
                    .setContentText("للحفاظ على تشغيل الأذان في وقته").setOngoing(true);
            Notification n = b.build();
            if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(7, n);
        } catch (Throwable ignored) {}
        h.post(tick);
        // عند تشغيل الشاشة / فك القفل: افتح التطبيق
        try {
            scr = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { launchUI(c); h.postDelayed(() -> launchUI(c), 2500); }
            };
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(scr, f);
        } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        Scheduler.schedule(this);
        if (i != null && i.getBooleanExtra("boot", false)) {
            // بعد الإقلاع قد تظهر الواجهة الرئيسية للتلفاز متأخرة، فنكرر المحاولة
            for (int d : new int[]{2000, 10000, 25000, 45000}) h.postDelayed(() -> launchUI(KeepAliveService.this), d);
        }
        return START_STICKY;
    }

    /** شبكة أمان: إذا مرّ وقت الأذان ولم يصل المنبّه ننفّذه من هنا */
    private void check() {
        try {
            long ni = getSharedPreferences("p", 0).getLong("nextIq", 0), t0 = System.currentTimeMillis();
            if (Prefs.iqOn(this) && ni > 0 && t0 >= ni && t0 - ni < 120000 && Fire.claimIq(this, ni)) {
                Fire.goIq(getApplicationContext(), () -> {});
                Scheduler.schedule(this);
            }
            if (!Prefs.on(this)) return;
            long nt = getSharedPreferences("p", 0).getLong("next", 0), now = System.currentTimeMillis();
            if (nt <= 0 || now < nt) return;
            if (now - nt < 300000) {
                if (Fire.claim(this, nt)) Fire.go(getApplicationContext(), () -> {});
            }
            Scheduler.schedule(this);
        } catch (Throwable ignored) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() { try { if (scr != null) unregisterReceiver(scr); } catch (Throwable ignored) {} h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
