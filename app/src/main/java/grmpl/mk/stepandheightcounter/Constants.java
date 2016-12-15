package grmpl.mk.stepandheightcounter;


final class Constants {

    // how many pressure values to average over
    final static int  mPRESSURE_AVG_COUNT=5;  // 5 should be about 1 second with 200msec delay(SENSOR_DELAY_NORMAL)

    // how many pressure averaged values do we for async correlation?
    final static int mMAX_PRESSURE_SAVE = 400;  // should be enough for more than 6 minutes with 1/sec

    // nanoseconds in one second (sensor event is in nanoseconds)
    final static long mNANO_IN_SECONDS = 1000000000;

    // nanoseconds in milliseconds
    final static long mNANO_IN_MILLISECONDS = 1000000;

    // milliseconds in one year
    final static long mMILLISECONDS_IN_YEAR = 31536000000L;

    // Notification ID
    static final int mNOTIID = 1111;

    // Preferences
    final static String mPREF_DEBUG = "pref_debug";
    final static String mPREF_TARGET_STEPS = "pref_target_steps";
    final static String mPREF_TARGET_HEIGHT = "pref_target_height";
    final static String mPREF_STAT_DETAIL = "pref_stat_detail";
    final static String mPREF_STAT_DETAIL_CLEAR = "pref_stat_detail_clear";
    final static String mPREF_STAT_DETAIL_CLEAR_NUM = "pref_stat_detail_clear_num";
    final static String mPREF_STAT_HOUR_MIN = "pref_stat_hour_min";
    final static String mPREF_STAT_HOUR ="pref_stat_hour";
    final static String mPREF_STAT_HOUR_CLEAR ="pref_stat_hour_clear";
    final static String mPREF_STAT_HOUR_CLEAR_NUM ="pref_stat_hour_clear_num";
    final static String mPREF_STAT_DAILY ="pref_stat_daily";

    // Files
    final static String mDIRECTORY = "StepandHeightCounter";
    final static String mFILENAME_STAT_DETAIL = "_statistics_detail.csv";
    final static String mSTAT_TYPE_SENS = "Sensor";
    final static String mSTAT_TYPE_MARK = "Marker";
    final static String mSTAT_TYPE_START = "Start";
    final static String mSTAT_TYPE_STOP = "Stop";
    final static String mSTAT_TYPE_RESET = "Reset";
    final static String mSTAT_TYPE_REGULAR = "Regular_Statistics";
    final static String mSTAT_TYPE_DAILY = "Daily_Statistics";
    final static String mFILENAME_STAT_REG = "_statistics_regular.csv";
    final static String mFILENAME_STAT_DAILY = "_statistics_daily.csv";



    // wakelock durations
    final static long mWAKELOCK_ALARM = 10000; // 10 seconds - just checking
    final static long mWAKELOCK_TRIGGER = 30000; // 30 seconds when motion is detected
    final static long mWAKELOCK_SETTLE_PRESSURE = 5000; // for pressure sensor initialization

    // alarm interval
    final static long mALARM_INTERVAL = 150000; // 4 minutes was too long sometimes, let's take 2 minutes

    // delay correlation a little bit, to wait for pressure
    final static long mDELAY_CORRELATION_FIRST = 1000;
    // delay again, if pressure is still not actual
    final static long mDELAY_CORRELATION = 2000; // 2 seconds should be more than enough

    // time to wait for pressure sensor to settle down at the beginning
    final static long mPRESSURE_SETTLE_DURATION = mNANO_IN_SECONDS;

    // normal pressure on sea level
    final static float mPRESSURE_SEA = 1013.25F;

    // init-value for height reference
    final static float mINIT_HEIGHT_REF = -9999;

    // we take care of new step sensor values only if more steps than this were taken
    final static int mMIN_STEPS_DELTA = 3;

    // if mean step duration is greater than this, we assume there was a walking pause
    //  and don't count it (standing, driving, elevator, ...)
    final static int  mMAX_STEP_DURATION = 5;

    // if elevation gain is more than this in m/sec we assume, there is somthing wrong
    //  and don't count it
    final static float mMAX_ELEV_GAIN = 1.0F;  // 1.0 m/sec are 60m/min - very hard to make

    // if 1m of elevation gain/loss takes longer than this in seconds, we don't count it
    //  it's not possible to distinguish between very slow ascending and atmospheric pressure change
    final static int mMAX_DURATION_1M = 60; // elevation gain less than 1m/min

    // update interval for height in activity
    final static int mINTERVAL_UPDATE_HEIGHT = 2000;

    // values to save to statistics file if no value for interval is available
    final static int mNOTRUNNING = -1;
    final static int mISRUNNING = 0;
}
