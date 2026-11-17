package sy.homs.dash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AdhanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            KeepAliveService.start(c, !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a));
        }
        if (Scheduler.ACT.equals(a) && Prefs.on(c)) {
            long t = i.getLongExtra("t", 0);
            if (Fire.claim(c, t)) {
                final PendingResult pr = goAsync();
                Fire.go(c.getApplicationContext(), pr::finish);
            }
            KeepAliveService.start(c);
        }
        if (Scheduler.ACT_IQ.equals(a) && Prefs.iqOn(c)) {
            long t = i.getLongExtra("t", 0);
            if (Fire.claimIq(c, t)) {
                final PendingResult pr = goAsync();
                Fire.goIq(c.getApplicationContext(), pr::finish);
            }
            KeepAliveService.start(c);
        }
        Scheduler.schedule(c);
    }
}
