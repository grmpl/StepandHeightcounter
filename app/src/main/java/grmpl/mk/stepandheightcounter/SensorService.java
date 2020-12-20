package grmpl.mk.stepandheightcounter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

import static grmpl.mk.stepandheightcounter.Constants.*;
import static java.lang.Math.pow;


public class SensorService extends Service {

    // ** Simple objects **
    // remember if listeners are already running
    private boolean mRegistered = false;

    // correlate sensor timestamp and real time
    private long          mTimestampDeltaMilliSec;
    // counting the steps
    private float[]       mStepsCumul = {0,-1,-1}; // 0: detail, 1: regulary, 2: daily
    private float         mStepsSensBefore = 0;
    // atmospheric pressure, mpressure is initialized negative!
    private float         mPressure = -1, mPressureTemporary = 0, mPressureZ = cPRESSURE_SEA;
    private int           mPressCount = 0;
    // for measuring the settling time of pressure sensor
    private long          mPressStartTimestamp = 0; //nanoseconds

    // counting height
    private float[]       mHeightCumul = {0,-1,-1}; // 0: detail, 1: regulary, 2: daily
    private float         mHeightBefore = 0;
    // Reference height
    private long          mHeightRefTimestamp = 0;  //nanoseconds
    private float         mHeightRef = cINIT_HEIGHT_REFCAL;

    // timestamp of sensor events (nanoseconds!)
    private long mEvtTimestampMilliSec = 0; // we only need one timestamp for all *Cumul-values


    // Remember calibration height for first pressure measurement
    private float         mCalibrationHeight = cINIT_HEIGHT_REFCAL;

    // ** External classes **
    // * My own classes *
    // SaveData
    private SaveData mSave;

    // * external *
    // to return a binder-Object
    private IBinder               mBinder = new LocalBinder();
    // SensorManager, to handle the sensors
    private SensorManager mSensorManager;
    // Sensors: Step-Sensor, pressure sensor and significant motion sensor
    private Sensor        mStepSensor, mBarometer, mMotion;

    // Start and stop alarm
    private AlarmReceiver mAlarm;

    //Preferences
    private SharedPreferences mSettings;

    //Notification Manager to update notification
    private NotificationManager mNotificationManager;

    //Pending Intent to call Activity from notification
    private PendingIntent mPIntentActivity;

    //For wakelocks
    private PowerManager mPowerManger;
    //Wakelock for settling of pressure sensor - acquire at first event, release at later event
    private PowerManager.WakeLock mWakelockSettle;

    // Was only needed for idleMessage, we now use standard messages
    // MessageQueue         mQueue;


    // ** Subclasses and arrays **

    // remember pressure for correlation
    private static class         mPressureHistory{
        float             pressure;
        long              timestamp; //nanoseconds
        mPressureHistory(float p, long t){
            pressure = p;
            timestamp = t;
        }
    }
    private ArrayList<mPressureHistory> mPressureHistoryList = new ArrayList<>();


    // Storing all data
    private class mStepSensorValues {
        long steptimestamp; // step sensor event timestamp in nanoseconds
        float stepstotal; // steps from sensor
        mStepSensorValues(long sts, float stt){
            steptimestamp = sts;
            stepstotal = stt;
        }
        String printdebug(float pressure, long pressuretimestamp, float height){
            String outline;
            SimpleDateFormat sdformati = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US); // fixed formatting, not local formatting
            // Correlation timestamp
            outline = sdformati.format(System.currentTimeMillis()) + ";";
            // Step event timestamp translated to real time
            outline = outline + sdformati.format(steptimestamp/ cNANO_IN_MILLISECONDS + mTimestampDeltaMilliSec) + ";";
            outline = outline + String.format(Locale.US,"%.3f; %.0f; %.0f; %.3f; %.3f; %.2f; %.3f; %.2f; %.2f \n",
                    (float)steptimestamp/ cNANO_IN_SECONDS,mStepsCumul[0],stepstotal,(float)pressuretimestamp/ cNANO_IN_SECONDS,pressure,height,
                    (float)mHeightRefTimestamp/ cNANO_IN_SECONDS,mHeightRef,mHeightCumul[0]);
            return outline;
        }
    }

    // We need a list to save the events and process (correlate) them asynchronously
    private ArrayList<mStepSensorValues> mStepHistoryList = new ArrayList<>();
    // for calculating delta we remember the last values
    //   we don't use the array, as it is difficult to handle init- and before-values
    private mStepSensorValues mStepValuesCorrBefore = null;

    /* See https://developer.android.com/reference/android/app/Service.html#LocalServiceSample
       Public class to access Service
     */
    class LocalBinder extends Binder {
        SensorService getServerInstance() {
            return SensorService.this;
        }
    }

    // ** Initialization **

    @Override
    public void onCreate() {
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        // Get our own thread and looper
        HandlerThread thread = new HandlerThread("SensorService", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        thread.getLooper();
        // Initialize our sensors
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //unfortunately neither StepCounter nor PressureSensor do have a wakeup-type
        // Step: No batching (fifo = 0 entries)
        mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER,false);
        // Pressure: Fifo: 300 entries, maxdelay: 10 seconds
        mBarometer = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE,false);
        // Significant motion - only this one does have a wakeup type
        mMotion = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION,true);
        // create an Alarm receiver
        mAlarm = new AlarmReceiver();
        // to save our data to disk
        mSave = new SaveData(this);

        // for notification update
        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // for wakelocks
        mPowerManger = (PowerManager)getSystemService(Context.POWER_SERVICE);

        // get our values back from previous session
        restorePersistent();
    }


    @Override
    public IBinder onBind(Intent intent){
        return mBinder;
    }

    // ** Start/Stop measuring **
    boolean startListeners() {
        System.out.println("sudeep: starting measurement...");
        mSave.saveDebugStatus("Start measurement requested");
        if (!mRegistered) {
            // this must finish, so request a wakelock
            PowerManager.WakeLock wakelock
                    = mPowerManger.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stepandheightcounter:START");
            wakelock.acquire(cWAKELOCK_ALARM); //that should be more than enough - we will release it

            // Build an intent for starting our MainActivity from notification
            Intent nIntent = new Intent(this, MainActivity.class);
            // this is necessary when activity is started from a service
            //  we don't need to reuse an existing acitvity, as our main activity is stateless
            nIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Create pendingIntent which can be given to notification builder
            mPIntentActivity = PendingIntent.getActivity(this, 0, nIntent, 0);

            // https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
            // https://stackoverflow.com/questions/53815261/bad-notification-for-startforeground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel(cCHANNEL_ID, cCHANNEL_NAME, NotificationManager.IMPORTANCE_NONE);
                chan.setLightColor(Color.BLUE);
                chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

                mNotificationManager.createNotificationChannel(chan);
            }

            // back stack creation seems not to be necessary (would it even be possible from service?)
            Notification noti = new NotificationCompat.Builder(this, cCHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_walkinsteps)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.step_is_counting))
                    .setContentIntent(mPIntentActivity)
                    .build();
            startForeground(cNOTIID, noti);

            // Check, if we have statistic data from previous run to save
            periodicStatistics(System.currentTimeMillis(),cNOTRUNNING);

            // if there was no measurement in this periodic intervall, cumul-values will be set to -1
            if (mHeightCumul[1] < 0) mHeightCumul[1] = 0;
            if (mHeightCumul[2] < 0) mHeightCumul[2] = 0;
            if (mStepsCumul[1] < 0) mStepsCumul[1] = 0;
            if (mStepsCumul[2] < 0) mStepsCumul[2] = 0;
            // change should be saved
            savePersistent();


            if (getDetailSave("a"))
            mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), cSTAT_TYPE_START);

            mAlarm.setAlarm(this);

            // Register only pressure sensor listener
            //  step sensor will be registered when pressure sensor settling time is over
            boolean succ = mSensorManager.registerListener(mSensorBarListener, mBarometer, SensorManager.SENSOR_DELAY_NORMAL);
            mRegistered = true;
            mSensorManager.requestTriggerSensor(mMotionListener, mMotion);
            // update display
            getValues();

            //now we can release the wakelock
            if (wakelock.isHeld()) wakelock.release();
            return succ;
        }
        // if we are already registered to sensors, we don't have to do anything
        else return true;
    }

    void stopListeners() {
        System.out.println("sudeep: stopping measurement...");
        savePersistent();

        mSave.saveDebugStatus("Stopping measurement");
        mSensorManager.unregisterListener(mSensorBarListener);
        mSensorManager.unregisterListener(mSensorStepListener);
        mSensorManager.cancelTriggerSensor(mMotionListener,mMotion);
        // make sure we have all work done
        correlateSensorEvents();

        if (getDetailSave("o"))
            mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), cSTAT_TYPE_STOP);


        //reset array
        mStepHistoryList.clear();
        mPressureHistoryList.clear();

        mStepValuesCorrBefore = null;
        mStepsSensBefore = 0;

        //reset pressure values
        mPressure = -1; //sensor settling and calibration has to be done again
        mPressStartTimestamp = 0; // pressure sensor will start anew
        mTimestampDeltaMilliSec = 0; // shouldn't be necessary, just make sure

        //after restart height reference will not be valid anymore
        mHeightRef = cINIT_HEIGHT_REFCAL;


        mAlarm.cancelAlarm(this);
        mRegistered = false;
        // update display
        getValues();
        // Now we don't care if we are killed
        stopForeground(true);

    }


    // ** most important method **

    /*
       Correlation: Loop over all outstanding step sensor events, correlate them with a pressure event,
        calculate elevation gain and save it.
        As we should have exact one handler for every array element, it should be sufficient
        to handle only one step sensor event, but messages could be lost and it should be more
        efficient to handle all outstanding events in one go, so we are looping.

       The correlation of stepsensor events and pressuresensor events must be done after all
        outstanding sensor events have been processed. Otherwise there could still be pressure events
        waiting, although all step events have been processed.
       Just calling the correlation process from step sensor does not work, because you don't get
        actual pressure values (still waiting to be processed).
       The idleHandler would be the best place for processing, because you can be sure all events
        are processed when queue is idle. Unfortunately this Handler is seldomly called in
        wakeup-situations and there seems to be no way to change this. So this does only work
        reliably with a constant wakelock.
       Just posting a runnable into the message-queue should put it in the end of the queue. But
        as it happens, there could still be outstanding pressure events. Maybe because of delayed
        delivery from the sensor (maxdelay of pressure sensor is up to 10 seconds and the flush-method
        is asynchronous!), maybe it's my batch-processing of all outstanding events (but already
        the first step events has a problem with delayed pressure events), maybe I'm just missing
        something.
       So the best way should be to use a post of a runnable and additionally check for delayed
        pressure and wait for delivery.
     */
    private void correlateSensorEvents() {
        // save actual size of list in variable, just to be sure it wouldn't be changed
        //  (shouldn't be necessary, but could be with async processing)
        int limit = mStepHistoryList.size(), calcindex;
        mSave.saveDebugStatus("correlation started");

        for (calcindex = 0; calcindex < limit; calcindex++) {

            int lowindex = 0, highindex = mPressureHistoryList.size() - 1, midindex = 0;
            long lasttimestamp;
            // always get first item of list to process, delete it after assignment
            mStepSensorValues values = mStepHistoryList.get(0);


            // check if all pressure events have been delivered - the last one must have
            //  a timestamp after the step sensor event, as pressure sensor events are  delivered continuously
            if (values.steptimestamp > (mPressureHistoryList.get(highindex).timestamp)) {
                mSave.saveDebugStatus("No actual pressure value, putting additional task in queue 2 seconds later");
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(this::correlateSensorEvents, cDELAY_CORRELATION);
                /* was
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        correlateSensorEvents();
                    }
                }, cDELAY_CORRELATION);
                */
                // Stop looping over step events, wouldn't make sense
                break;
            }


            // find pressure for my timestamp, timestamp is sorted, so we can make a binary search
            //  (this is a sample code from stackoverflow)
            while (lowindex <= highindex) {
                midindex = (lowindex + highindex) / 2;
                lasttimestamp = mPressureHistoryList.get(midindex).timestamp;
                if (values.steptimestamp < lasttimestamp) {
                    highindex = midindex - 1;
                } else if (values.steptimestamp > lasttimestamp) {
                    lowindex = midindex + 1;
                }
            }

            // if pressure values are valid, we check if statistic data has to be saved
            periodicStatistics(values.steptimestamp / cNANO_IN_MILLISECONDS
                    + mTimestampDeltaMilliSec, cISRUNNING);
            //  and afterwards we can delete our list entry (FIFO)
            //   we delete it here, because better it doesn't get counted, than counted twice
            mStepHistoryList.remove(0);



            // saving the found pressure values in temporary variables for better handling of code
            float pressure = mPressureHistoryList.get(midindex).pressure;
            long pressuretimestamp = mPressureHistoryList.get(midindex).timestamp;

            // Now we can calculate our height
            float height = calcHeight(pressure);

            if (mStepValuesCorrBefore == null) {  //no beforevalues yet: init and pause
                // just save reference value
                mHeightRef = height;
                // we use the step sensor timestamp as reference
                mHeightRefTimestamp = values.steptimestamp;
            } else { //normal values
                // first save the steps
                for (int i = 0; i < mStepsCumul.length; i++) {
                    mStepsCumul[i] =
                            mStepsCumul[i] + values.stepstotal - mStepValuesCorrBefore.stepstotal;
                }
                // and timestamp for event (we will only need msec in Unix time)
                mEvtTimestampMilliSec = values.steptimestamp / cNANO_IN_MILLISECONDS + mTimestampDeltaMilliSec;

                // then check if we have height to count
                // If steps take longer than 5 seconds (no walking, pausing, driving, ...)
                // or if we are ascending too fast,
                // or mHeightRef was resetted just now,
                // we just save height as new reference and don't count it for elevation gain

                if ( ( (values.steptimestamp - mStepValuesCorrBefore.steptimestamp)
                           / (values.stepstotal - mStepValuesCorrBefore.stepstotal)
                           > cMAX_STEP_DURATION * cNANO_IN_SECONDS
                     ) ||
                     ( (height - mHeightBefore)
                           / (values.steptimestamp - mStepValuesCorrBefore.steptimestamp)
                           > cMAX_ELEV_GAIN
                     ) ||
                     ( mHeightRef <= cINIT_HEIGHT_REFCAL)
                   ) {
                       mHeightRef = height;
                       mHeightRefTimestamp = values.steptimestamp;
                } else {
                    // Here is the only place where we count the ascending
                    // only count if ascending is greater than 1m
                    // lower values could be everything (e.g. atmospheric pressure change)
                    if ((height - mHeightRef) >= 1) {
                        for (int i = 0; i < mHeightCumul.length; i++) {
                            mHeightCumul[i] = mHeightCumul[i] + height - mHeightRef;
                        }
                        mHeightRef = height;
                        mHeightRefTimestamp = values.steptimestamp;
                    } else {
                        // if we are descending or slow ascending: just save new height as reference, don't count it
                        //   slow ascending/descending: after mMAX_DURATION_1M seconds height difference is less than +-1m
                        //   descending: height difference is less than 1m negative
                        //         (please note: slow descending should also lead to new reference height only, if 1 minute has passed
                        //                       so -1 for second comparison is correct, not 0
                        //  note: timestamp is nanoseconds
                        if (((values.steptimestamp - mHeightRefTimestamp) > cMAX_DURATION_1M * cNANO_IN_SECONDS)
                                || ((height - mHeightRef) <= -1)) {
                            mHeightRef = height;
                            mHeightRefTimestamp = values.steptimestamp;
                        }
                    }

                }

            }


            mStepValuesCorrBefore = values;
            mHeightBefore = height;

            // ** Update activity, notification, debug-information and persistent values **
            savePersistent();

            if (mSettings.getBoolean(cPREF_DEBUG, false)) mSave.saveDebugValues(
                    values.printdebug(pressure, pressuretimestamp, height));

            if (getDetailSave("e"))
                mSave.saveStatistics(mEvtTimestampMilliSec,
                        mStepsCumul[0], mHeightCumul[0], height, cSTAT_TYPE_SENS);

            // https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
            // https://stackoverflow.com/questions/53815261/bad-notification-for-startforeground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel(cCHANNEL_ID, cCHANNEL_NAME, NotificationManager.IMPORTANCE_NONE);
                chan.setLightColor(Color.BLUE);
                chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

                mNotificationManager.createNotificationChannel(chan);
            }

            // Update Notification, put actual values in it
            Notification noti = new NotificationCompat.Builder(this, cCHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_walkinsteps)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.steps) + ": "
                            + String.format(Locale.getDefault(), "%.0f", mStepsCumul[0])
                            + " " + getString(R.string.height_accumulated) + ": "
                            + String.format(Locale.getDefault(), "%.1f", mHeightCumul[0]))
                    .setContentIntent(mPIntentActivity)
                    .build();
            mNotificationManager.notify(cNOTIID, noti);


            // Callback to MainActivity
            Intent callback = new Intent();
            callback.setAction("grmpl.mk.stepandheighcounter.custom.intent.Callback");
            if (mSettings.getBoolean(cPREF_DEBUG, false)) {
                String outtext = getString(R.string.out_stat_listlength) + mStepHistoryList.size() + "\n";
                outtext = outtext + getString(R.string.out_stat_pressure) + pressure + "\n";
                outtext = outtext + getString(R.string.out_stat_referenceheight) + mHeightRef + "\n";
                callback.putExtra("Status", outtext);
            } else callback.putExtra("Status", " ");
            callback.putExtra("Steps", mStepsCumul[0]);
            callback.putExtra("Height", height);
            callback.putExtra("Heightacc", mHeightCumul[0]);
            callback.putExtra("Registered", true);
            callback.putExtra("Stepstoday", mStepsCumul[2]);
            callback.putExtra("Heighttoday", mHeightCumul[2]);
            sendBroadcast(callback);

        }

        mSave.saveDebugStatus("correlation loop finished, " + calcindex +
                "/" + limit +
                ") items processed");
            /* make sure we are registered to the significant motion wakeup sensor
                If we just do this from the end of the triggered method, the registration can
                get lost (I've seen this after some hours of running).
                As there is no easy way to check if we are already registered, it's better just
                to register again.
                Just register again doesn't help - maybe unregister and reregister does the trick.
                (haven't checked in detail)
             */

        mSensorManager.cancelTriggerSensor(mMotionListener, mMotion);
        mSensorManager.requestTriggerSensor(mMotionListener, mMotion);
        mSave.saveDebugStatus("Register to significant motion sensor from correlation task");

    }

    // ** Listeners for sensors **

    private TriggerEventListener mMotionListener = new TriggerEventListener() {

        @Override
        public void onTrigger(TriggerEvent triggerEvent) {
            PowerManager.WakeLock wakelock;
            // do nothing, just acquire wakelock to let sensor events come through
            wakelock = mPowerManger.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stepandheightcounter:SIGNIFICANT_MOTION");
            wakelock.acquire(cWAKELOCK_TRIGGER); //acquire for 30sec - this should not be a single move only
            mSave.saveDebugStatus("Wake up from trigger");
            mSensorManager.flush(mSensorBarListener);
            mSensorManager.flush(mSensorStepListener);
            //and register again
            mSensorManager.requestTriggerSensor(mMotionListener,mMotion);
            mSave.saveDebugStatus("Register to significant motion sensor from trigger");
            // unfortunately, trigger seems to be lost after some hours of operation,
            // so we register after every correlation, too (see above)
        }
    };

    private SensorEventListener mSensorBarListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            // If we are just starting, we have to wait for sensor to settle, as in the
            //  first 1-2 seconds values can change significantly.
            // We use this time to calculate the offset between sensor timestamp and real time
            if (mPressure < 0) {
                // first event
                if (mPressStartTimestamp == 0){
                    // get a wakelock, we have to finish this
                    mWakelockSettle = mPowerManger.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"stepandheightcounter:SENSOR_SETTLE");
                    mWakelockSettle.acquire(cWAKELOCK_SETTLE_PRESSURE);
                    mSave.saveDebugStatus("First pressure value, waiting 1 sec for sensor to settle down");
                    mPressStartTimestamp = sensorEvent.timestamp;
                    // calculate first timestampdelta
                    mTimestampDeltaMilliSec = System.currentTimeMillis()-(sensorEvent.timestamp / 1000000);
                }
                // settling time
                else if (sensorEvent.timestamp-mPressStartTimestamp < cPRESSURE_SETTLE_DURATION) {
                    // do nothing, just adjust timestampdelta
                    long tsdelta = System.currentTimeMillis()-(sensorEvent.timestamp / 1000000);
                    // timestampdelta must be positive (current time must be more than sensor timestamp)
                    //  so if delta is smaller it'a a better value
                    if ( tsdelta < mTimestampDeltaMilliSec )
                        mTimestampDeltaMilliSec = tsdelta;
                } // time is over - start measurement
                else {
                    mSave.saveDebugStatus("Wating time over, starting Step sensor");
                    // register StepSensor
                    boolean succ = mSensorManager.registerListener(mSensorStepListener, mStepSensor,
                            SensorManager.SENSOR_DELAY_NORMAL);
                    System.out.println("sudeep: val: succ:" + succ);
                    if (!succ){
                        Intent callback = new Intent();
                        callback.setAction("grmpl.mk.stepandheighcounter.custom.intent.Callback");
                        callback.putExtra("Status",getString(R.string.stepsensor_not_activated));
                        sendBroadcast(callback);
                        mSave.saveDebugStatus("Error in registering step sensor.");
                        stopListeners();
                    } else
                        mPressure = 0;
                    // wakelock can be released
                    if (mWakelockSettle.isHeld()) mWakelockSettle.release();
                }
            } else {
                // We average over 5 values, this would be ~1sec
                mPressureTemporary = mPressureTemporary + sensorEvent.values[0];
                if (mPressCount == (cPRESSURE_AVG_COUNT - 1)) {
                    // mPressure is just the last valid average over 5 pressure values
                    //  if there is a problem with getting pressure values, we won't notice
                    mPressure = mPressureTemporary / cPRESSURE_AVG_COUNT;
                    // always check if calibration is needed
                    if(mCalibrationHeight > cINIT_HEIGHT_REFCAL) calibrateHeight(mCalibrationHeight);

                    // remember the last 400 values of pressure
                    mPressureHistory pressure = new mPressureHistory(mPressure, sensorEvent.timestamp);
                    mPressureHistoryList.add(pressure);
                    if (mPressureHistoryList.size() > cMAX_PRESSURE_SAVE)
                        mPressureHistoryList.remove(0);
                    if(mSettings.getBoolean(cPREF_DEBUG,false)){
                        mSave.saveDebugStatus(
                                String.format(Locale.US, "Pressure value %.2f saved, listsize: %d",
                                        mPressure,mPressureHistoryList.size())
                        );
                    }
                    mPressCount = 0;
                    mPressureTemporary = 0;
                } else mPressCount++;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
            //Don't care
        }
    };

    private SensorEventListener mSensorStepListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Don't care
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            long steptimestamp;
            float stepsact = event.values[0];
            steptimestamp = event.timestamp;
            /* first sensor reading after pause or initialization
                initialize mStepsBefore and save initial array element with reference values
             */
            if (mStepsSensBefore == 0) {
                mStepsSensBefore = stepsact;
                mStepSensorValues data = new mStepSensorValues(steptimestamp, stepsact);
                mStepHistoryList.add(data);
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(SensorService.this::correlateSensorEvents,cDELAY_CORRELATION_FIRST);
                /* was
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        correlateSensorEvents();
                    }
                },cDELAY_CORRELATION_FIRST);
            */
                mSave.saveDebugStatus("Step Counter init.");
            }
            /*
            Counted reading
             (sensor is counting total steps, so we don't have to care about every single step)
            */
            else if ((stepsact - mStepsSensBefore) >cMIN_STEPS_DELTA) {
                // just save the data
                mStepSensorValues data = new mStepSensorValues(steptimestamp, stepsact);
                mStepHistoryList.add(data);
                // and remember it for next check
                mStepsSensBefore = stepsact;
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(SensorService.this::correlateSensorEvents,cDELAY_CORRELATION_FIRST);
                /* was
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        correlateSensorEvents();
                    }
                },cDELAY_CORRELATION_FIRST);
                */
                mSave.saveDebugStatus("Step Counter event saved");
            }
            // else do nothing, not enough steps to care
        }
    };

    // ** Methods for Activity and helpers **

    void calibrateHeight(float cHeight){
        // if pressure is already measured, calculate sea level pressure from actual pressure
        if (mPressure > 0) {
            if (getDetailSave("c"))
                mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), getString(R.string.stat_type_calibration_before));

            // get pressure of height reference (saving it would be more difficult than reextracting
            //   as calibration would be done seldom)
            double heightrefpressure = mPressureZ * pow(1-(mHeightRef * 0.0065 / 288.15),5.255);
            // calculate new mPressureZ
            mPressureZ = (float) (mPressure / pow((1 - (cHeight * 0.0065 / 288.15)), 5.255));
            // calculate new height reference
            mHeightRef = (float) ((1 - pow((heightrefpressure / mPressureZ), (1 / 5.255))) * 288.15 / 0.0065);
            SharedPreferences.Editor editpref = mSettings.edit();
            editpref.putFloat("mPressureZ",mPressureZ);
            editpref.apply();
            // calibration done, temporary value must be cleared
            mCalibrationHeight = cINIT_HEIGHT_REFCAL;
            if (getDetailSave("c"))
                mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), getString(R.string.stat_type_calibration_after));

        }
        // if there is no pressure yet, save value for later calibration
        else mCalibrationHeight = cHeight;
    }

    void resetData() {
        if (getDetailSave("r"))
            mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), getString(R.string.stat_type_reset_before) );
        mStepsCumul[0] = 0;
        mPressureZ = cPRESSURE_SEA;
        mHeightRef = cINIT_HEIGHT_REFCAL; // 0 as init-value would not work at sea
        mHeightCumul[0] = 0;
        savePersistent();
        if (getDetailSave("r"))
            mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), getString(R.string.stat_type_reset_after) );
    }

    void getValues() {
        // if we are measuring, update statistic values
        if (mRegistered) periodicStatistics(System.currentTimeMillis(),cISRUNNING);
        else periodicStatistics(System.currentTimeMillis(),cNOTRUNNING);

        Intent callback = new Intent();
        callback.setAction("grmpl.mk.stepandheighcounter.custom.intent.Callback");

        if (mSettings.getBoolean(cPREF_DEBUG, false)) {
            String outtext = getString(R.string.out_stat_listlength) + mStepHistoryList.size() + "\n";
            outtext = outtext + getString(R.string.out_stat_pressure) + mPressure + "\n";
            outtext = outtext + getString(R.string.out_stat_referenceheight) + mHeightRef + "\n";
            callback.putExtra("Status", outtext);
        } else callback.putExtra("Status", " ");
        callback.putExtra("Steps",mStepsCumul[0]);
        callback.putExtra("Height",getHeight());
        callback.putExtra("Heightacc",mHeightCumul[0]);
        callback.putExtra("Registered",mRegistered);
        callback.putExtra("Stepstoday", mStepsCumul[2]);
        callback.putExtra("Heighttoday", mHeightCumul[2]);

        sendBroadcast(callback);
    }

    private boolean getDetailSave(String identifier){
        Set<String> detail_multi = mSettings.getStringSet(cPREF_STAT_DETAIL_MULTI, cPREF_STAT_DETAIL_MULTI_DEFAULT);

        assert detail_multi != null;
        for (String s:  detail_multi ) {
            if ( s.equals(identifier) ) return true;
        }

        return false;
    }

    float getHeight(){

        if (mPressure <= 0) return 9998F; // no measurement yet
        else return calcHeight(mPressure);
    }

    private float calcHeight(float pressure){
        //According to Wikipedia this is the "official" international hypsometric formula
        // on http://keisan.casio.com/ there is a similar, but different formula
        // I don't know which is the better one.
        return (float) ((1 - pow((pressure / mPressureZ), (1 / 5.255))) * 288.15 / 0.0065);
    }

    private void savePersistent() {
        SharedPreferences.Editor editpref = mSettings.edit();
        // only steps, elevation gain and calibration is important to remember
        //  all other values will be calculated again after init
        for ( int i = 0; i < mStepsCumul.length; i++){
            editpref.putFloat("mStepsCumul" + i, mStepsCumul[i]);
            editpref.putFloat("mHeightCumul" + i, mHeightCumul[i]);
        }
        editpref.putLong("mEvtTimestampMilliSec", mEvtTimestampMilliSec);
        editpref.putFloat("mPressureZ", mPressureZ);
        editpref.apply();

    }

    private void restorePersistent() {
        // only steps, elevation gain and calibration is important to remember
        //  all other values will be calculated again after init
        for ( int i = 0; i < mStepsCumul.length; i++){
            mStepsCumul[i]   = mSettings.getFloat("mStepsCumul" + i, 0);
            mHeightCumul[i]  = mSettings.getFloat("mHeightCumul" + i, 0);
        }
        mEvtTimestampMilliSec = mSettings.getLong("mEvtTimestampMilliSec", 0);
        mPressureZ = mSettings.getFloat("mPressureZ", cPRESSURE_SEA);
    }

    private void periodicStatistics(long acttimestamp_msec, int running){  //acttimestamp in milliseconds
        // Check, if we have to save statistic data
        //   we look for timestamp of last saved event and compare it to actual timestamp
        //   if it is in previous interval:
        //         save statistics to file
        //         save -1 or 0-values to file for all other intervals until the one before now
        //           (no data collected)
        //         set statistic data to zero
        //   if it is in current interval:
        //         do nothing, let it cumulate further on until interval finshes
        // Check will be done on start of listener with acttimestamp = currenttime and running = -1,
        //  so all interval-timestamps were app was not running will be saved with -1-values for step and height.
        //   (we can use variables, as they are restored by onCreate after app restart)
        // And check will be done on correlate with acttimestamp = timestamp of current event and running = 0
        //  so all interval-timestamps were app was running, but no event happened will be saved
        //  with 0-values. acttimestamp = timestamp of event assures that logic is working even
        //  if correlation is done long after events have happened.

        // evttimestamp must be already in Unix time

        TimeZone tz = TimeZone.getDefault();
        boolean setevttimestamp = false;

        // To save time for checks, we do a quick check if saved statistics is in previous 15min-Interval
        //    if not, we don't have to care for all the other checks and just do nothing
        //    (shortest interval is 15 minutes, all intervals are aligned to 15 minutes)

        if( mEvtTimestampMilliSec > cMILLISECONDS_IN_YEAR &&  // plausibility: mEvtTimestampMilliSec should be at least in 1971
                (mEvtTimestampMilliSec / (15 * 60 * 1000) < acttimestamp_msec / (15 * 60 * 1000))) {
            // Do we have to save regular statistics?
            if (mSettings.getBoolean(cPREF_STAT_HOUR, false)){
                // get the interval duration for regular statistics
                //  int would be enough, but careful casting would be necessary
                long interval_msec = Integer.parseInt(Objects.requireNonNull(mSettings.getString(cPREF_STAT_HOUR_MIN, "30"))) * 60 * 1000;
                long evtint = mEvtTimestampMilliSec / interval_msec; // integer-division: correct sequence is necessary!
                long currint = acttimestamp_msec / interval_msec;
                // if we have values from previous interval, last interval is finished, we can save them
                if ( evtint < currint ){
                    // we save with timestamp at end of interval, i.e. evtint+1
                    //  drawback: last interval of day is saved at next day 0:00
                    //    solution: check in save-method for 0:00 and save it as 24:00
                    mSave.saveStatistics( (evtint + 1) * interval_msec,
                            mStepsCumul[1],mHeightCumul[1],cSTAT_TYPE_REGULAR);
                    mStepsCumul[1] = running;  // method can be called even if measurement is not running
                    mHeightCumul[1] = running; // so we have to save actual state
                    // set evttimestamp, otherwise this function will be called repeatedly until next event (steps!) is saved
                    //  please note: regular intervals have to be a integral divisor of day, otherwise we would need two different
                    //              event timestamps
                    setevttimestamp = true;
                    // fill up all intervals without values, but limit it to 100 entries
                    //   otherwise we could write lots of data if app was not used for a long time.
                    // 100 lines did not give a delay at start when tested and would be enough for one day.
                    if (currint - evtint > 100) evtint = ( 24*60*60*1000 * ( acttimestamp_msec/(24 * 60 * 60 * 1000) )
                            - tz.getOffset(acttimestamp_msec) ) / interval_msec - 1 ;
                    for (long l = evtint + 2; l <= currint; l++)
                        mSave.saveStatistics( l * interval_msec, running, running,cSTAT_TYPE_REGULAR);
                }
                //else nothing to do
            }
            // do we have to save daily statistics?
            if (mSettings.getBoolean(cPREF_STAT_DAILY, false)){
                // get the interval duration for regular statistics
                long interval_msec = 24 * 60 * 60 * 1000;//24h hours
                // We calculate our days based on current timezone:
                long evtint = ( mEvtTimestampMilliSec + tz.getOffset(mEvtTimestampMilliSec) ) / interval_msec; //int is enough here
                long currint = ( acttimestamp_msec + tz.getOffset(acttimestamp_msec) )/ interval_msec;
                // if we have values from previous interval, last interval is finished, we can save them
                if ( evtint < currint ){
                    // as our days are UTC-days ( integer * 24h ), we have to correct the timestamps with timezone-information
                    //   timestamp for saving would be 0:00
                    mSave.saveStatistics( evtint * interval_msec - tz.getOffset(mEvtTimestampMilliSec),
                            mStepsCumul[2],mHeightCumul[2],cSTAT_TYPE_DAILY);
                    mStepsCumul[2] = running;  //see above
                    mHeightCumul[2] = running; //see above
                    setevttimestamp = true;
                    // fill up all intervals without values, but not more than one week
                    if (currint - evtint > 8) evtint = currint - 8;
                    for (long l = evtint + 1; l < currint; l++)
                        mSave.saveStatistics( l * interval_msec - tz.getOffset(mEvtTimestampMilliSec), running, running, cSTAT_TYPE_DAILY);
                }
                //else nothing to do
            }
            if (setevttimestamp){
                mEvtTimestampMilliSec = acttimestamp_msec;
                savePersistent();
            }

        }
    }

    // ** Final actions **

    // It is not guaranteed, that this will be called, so don't rely on it
    @Override
    public void onDestroy() {
        savePersistent(); // should be done in stopListeners, but maybe we don't reach this method

        stopListeners();

        mAlarm.cancelAlarm(this);

        super.onDestroy();
    }
}
