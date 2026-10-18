package family.tv.companion;
import android.content.*;

public final class LocalAlarmReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){new LocalData(c).restoreAlarms();return;}
        if("duobuddy.timer".equals(intent.getAction()))c.startForegroundService(new Intent(c,CompanionService.class).setAction("local_timer").putExtra("id",intent.getIntExtra("id",-1)));
    }
}
