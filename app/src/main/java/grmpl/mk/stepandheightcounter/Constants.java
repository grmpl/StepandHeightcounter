package grmpl.mk.stepandheightcounter;


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class Constants {

    // how many pressure values to average over
    final static int  cPRESSURE_AVG_COUNT=5;  // 5 should be about 1 second with 200msec delay(SENSOR_DELAY_NORMAL)

    // how many pressure averaged values do we for async correlation?
    final static int cMAX_PRESSURE_SAVE = 400;  // should be enough for more than 6 minutes with 1/sec

    // nanoseconds in one second (sensor event is in nanoseconds)
    final static long cNANO_IN_SECONDS = 1000000000;

    // nanoseconds in milliseconds
    final static long cNANO_IN_MILLISECONDS = 1000000;

    // milliseconds in one year
    final static long cMILLISECONDS_IN_YEAR = 31536000000L;

    // Notification ID
    static final int cNOTIID = 1111;

    // Notification Channel ID
    final static String cCHANNEL_ID = "step_height_channel";

    // Preferences
    final static String cPREF_DEBUG = "pref_debug";
    final static String cPREF_TARGET_STEPS = "pref_target_steps";
    final static String cPREF_TARGET_HEIGHT = "pref_target_height";
    // final static String cPREF_STAT_DETAIL = "pref_stat_detail";
    final static String cPREF_STAT_DETAIL_MULTI = "pref_stat_detail_multi";
    final static String cPREF_STAT_DETAIL_CLEAR = "pref_stat_detail_clear";
    final static String cPREF_STAT_DETAIL_CLEAR_NUM = "pref_stat_detail_clear_num";
    final static String cPREF_STAT_HOUR_MIN = "pref_stat_hour_min";
    final static String cPREF_STAT_HOUR ="pref_stat_hour";
    final static String cPREF_STAT_HOUR_CLEAR ="pref_stat_hour_clear";
    final static String cPREF_STAT_HOUR_CLEAR_NUM ="pref_stat_hour_clear_num";
    final static String cPREF_STAT_DAILY ="pref_stat_daily";
    private static final String[] cPREF_STAT_DETAIL_MULTI_DEFAULT_VALUES = new String[] { "m" };
    static final Set<String> cPREF_STAT_DETAIL_MULTI_DEFAULT = new HashSet<>(Arrays.asList(cPREF_STAT_DETAIL_MULTI_DEFAULT_VALUES));

    // Files
    final static String cDIRECTORY = "StepandHeightCounter";
    final static String cFILENAME_STAT_DETAIL = "_statistics_detail.csv";
    final static String cSTAT_TYPE_SENS = "Sensor";
    final static String cSTAT_TYPE_MARK = "Marker";
    final static String cSTAT_TYPE_START = "Start";
    final static String cSTAT_TYPE_STOP = "Stop";
    final static String cSTAT_TYPE_REGULAR = "Regular_Statistics";
    final static String cSTAT_TYPE_DAILY = "Daily_Statistics";
    final static String cFILENAME_STAT_REG = "_statistics_regular.csv";
    final static String cFILENAME_STAT_DAILY = "_statistics_daily.csv";



    // wakelock durations
    final static long cWAKELOCK_ALARM = 10000; // 10 seconds - just checking
    final static long cWAKELOCK_TRIGGER = 30000; // 30 seconds when motion is detected
    final static long cWAKELOCK_SETTLE_PRESSURE = 5000; // for pressure sensor initialization

    // alarm interval
    final static long cALARM_INTERVAL = 150000; // 4 minutes was too long sometimes, let's take 2 minutes

    // delay correlation a little bit, to wait for pressure
    final static long cDELAY_CORRELATION_FIRST = 1000;
    // delay again, if pressure is still not actual
    final static long cDELAY_CORRELATION = 2000; // 2 seconds should be more than enough

    // time to wait for pressure sensor to settle down at the beginning
    final static long cPRESSURE_SETTLE_DURATION = cNANO_IN_SECONDS;

    // normal pressure on sea level
    final static float cPRESSURE_SEA = 1013.25F;

    // init-value for height reference
    final static float cINIT_HEIGHT_REFCAL = -9999;

    // we take care of new step sensor values only if more steps than this were taken
    final static int cMIN_STEPS_DELTA = 3;

    // if mean step duration is greater than this, we assume there was a walking pause
    //  and don't count it (standing, driving, elevator, ...)
    final static int  cMAX_STEP_DURATION = 5;

    // if elevation gain is more than this in m/sec we assume, there is somthing wrong
    //  and don't count it
    final static float cMAX_ELEV_GAIN = 1.0F;  // 1.0 m/sec are 60m/min - very hard to make

    // if 1m of elevation gain/loss takes longer than this in seconds, we don't count it
    //  it's not possible to distinguish between very slow ascending and atmospheric pressure change
    final static int cMAX_DURATION_1M = 60; // elevation gain less than 1m/min

    // update interval for height in activity
    final static int cINTERVAL_UPDATE_HEIGHT = 2000;

    // values to save to statistics file if no value for interval is available
    final static int cNOTRUNNING = -1;
    final static int cISRUNNING = 0;
}
