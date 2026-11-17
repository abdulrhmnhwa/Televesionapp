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
import android.app.PendingIntent;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** خدمة أمامية خفيفة تبقي التطبيق حيّاً بالخلفية ليعمل الأذان في وقته */
public class KeepAliveService extends Service {
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { check(); h.postDelayed(this, 15000); }
    };

    private static final Handler MH = new Handler(Looper.getMainLooper());

    /** يفتح الشاشة الرئيسية إن لم تكن ظاهرة (عند الإقلاع أو تشغيل الشاشة) */
    static void launchUI(final Context c) {
        if (MainActivity.fg || !Prefs.auto(c)) return;
        final Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try { c.startActivity(i); } catch (Throwable ignored) {}
        // شبكة أمان: إن منع النظام الفتح من الخلفية، نعرض إشعاراً بشاشة كاملة (full-screen intent) يفتح التطبيق
        MH.postDelayed(() -> { if (!MainActivity.fg && Prefs.auto(c)) fullScreen(c, i); }, 1500);
    }

    static void fullScreen(Context c, Intent i) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("open", "فتح الشاشة الرئيسية", NotificationManager.IMPORTANCE_HIGH);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
                b = new Notification.Builder(c, "open");
            } else {
                b = new Notification.Builder(c).setPriority(Notification.PRIORITY_MAX);
            }
            PendingIntent pi = PendingIntent.getActivity(c, 8, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            b.setSmallIcon(R.drawable.ic_stat).setContentTitle("لوحة حمص").setContentText("اضغط لفتح الشاشة الرئيسية")
                    .setCategory(Notification.CATEGORY_ALARM).setAutoCancel(true)
                    .setContentIntent(pi).setFullScreenIntent(pi, true);
            nm.notify(8, b.build());
        } catch (Throwable ignored) {}
    }

    /** نافذة شفافة 1×1 بكسل: وجود نافذة ظاهرة مع إذن «الظهور فوق التطبيقات» يسمح بفتح التطبيق من الخلفية (أندرويد 10–15) */
    private View ov;
    private void addOverlay() {
        try {
            if (ov != null) return;
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            View v = new View(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(v, lp);
            ov = v;
        } catch (Throwable t) { ov = null; }
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
        addOverlay();
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
        addOverlay();
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
    @Override public void onDestroy() { try { if (scr != null) unregisterReceiver(scr); } catch (Throwable ignored) {} try { if (ov != null) ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(ov); } catch (Throwable ignored) {} ov = null; h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
