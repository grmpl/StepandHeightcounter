package grmpl.mk.stepandheightcounter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;


import java.util.Calendar;

import static grmpl.mk.stepandheightcounter.Constants.*;


// setting and receiving regular wakeup-alarms
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        PowerManager.WakeLock wakelock;
        // acquire wakelock to work on the messages in handler queue
        //  as we don't know, when work is finished, we just acquire it for some time
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ALARM");

        wakelock.acquire(cWAKELOCK_ALARM);

        SaveData save = new SaveData(context);
        save.saveDebugStatus("wake up from alarm");
    }

    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, grmpl.mk.stepandheightcounter.AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
            // awake every two minutes - significant motion sensor is not reliable enough

        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, cALARM_INTERVAL, cALARM_INTERVAL, pi);

    }

    // shut off alarm
    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, grmpl.mk.stepandheightcounter.AlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
}

