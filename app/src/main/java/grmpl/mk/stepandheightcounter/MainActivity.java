package grmpl.mk.stepandheightcounter;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import grmpl.mk.stepandheightcounter.SensorService.LocalBinder;

import static grmpl.mk.stepandheightcounter.Constants.*;


public class MainActivity extends AppCompatActivity {

    private TextView mStatusText, mHeightText, mStepText, mHeightaccText, mDecraccText, mTimeaccText, mStepDailyText, mHeightDailyText;
    private EditText mCalibrateIn;
    private Button mStartButton;
    private FloatingActionButton mFloatingButton;

    private ProgressBar mStepDailyProgress, mHeightDailyProgress;
    boolean mBounded = false, mRunning = false;
    private SensorService mSensService;
    private MyReceiver mReceiver = null;
    private SharedPreferences mSettings;
    private SaveData mSave;

    // https://stackoverflow.com/questions/42941662/request-permissions-in-main-activity
    public static final int MULTIPLE_PERMISSIONS = 100;

    // Todo: Permission check is a mess copied out of the internet and modified to the needs
    //       Should be reworked to something nicer like this one: https://stackoverflow.com/questions/34342816/android-6-0-multiple-permissions

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACTIVITY_RECOGNITION)
                + ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale
                    (MainActivity.this, Manifest.permission.ACTIVITY_RECOGNITION) ||
                    ActivityCompat.shouldShowRequestPermissionRationale
                            (MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(
                            new String[]{Manifest.permission.ACTIVITY_RECOGNITION,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MULTIPLE_PERMISSIONS);
                }

            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestPermissions(
                            new String[]{Manifest.permission.ACTIVITY_RECOGNITION,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MULTIPLE_PERMISSIONS);
                }
            }
        } else {
            // put your function here

        }
    }

    // this is the callback if permission dialog is ended - we just save the result for later
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean activityPermission=false;
        boolean writeExternalFile=false;

        switch (requestCode) {
            case MULTIPLE_PERMISSIONS:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        String test=permissions[i];
                        switch (permissions[i]){
                            case Manifest.permission.ACTIVITY_RECOGNITION:
                                activityPermission = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                                break;
                            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                                writeExternalFile = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                                break;
                        }

                    }

                    if(writeExternalFile) { // Permission on Android10+ not necessary, because of scoped storage
                        Toast.makeText(MainActivity.this, R.string.permission_sdcard_granted,
                                Toast.LENGTH_SHORT).show();
                        SharedPreferences.Editor editpref =
                                PreferenceManager.getDefaultSharedPreferences(this).edit();
                        editpref.putBoolean("mReqSDPermission", false);
                        editpref.apply();
                    } else if (Build.VERSION.SDK_INT <  Build.VERSION_CODES.Q) {
                        Toast.makeText(MainActivity.this, R.string.cant_write_sdcard,
                                Toast.LENGTH_SHORT).show();
                    }

                    if(activityPermission)
                    {
                        Toast.makeText(MainActivity.this, R.string.permission_ar_granted,
                                Toast.LENGTH_SHORT).show();
                        SharedPreferences.Editor editpref =
                                PreferenceManager.getDefaultSharedPreferences(this).edit();
                        editpref.putBoolean("mReqActivityRecognitionPermission", false);
                        editpref.apply();
                    } else {
                        Toast.makeText(MainActivity.this, R.string.cant_activity_recognition,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        Manifest.permission.ACTIVITY_RECOGNITION},
                                MULTIPLE_PERMISSIONS);
                    }
                }

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Request permissions
        checkPermission();

        // Access to persistent data
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        // Get all necessary elements
        mStatusText = (TextView)findViewById(R.id.textViewError);
        mHeightText = (TextView)findViewById(R.id.textViewHeightO);
        mHeightaccText = (TextView)findViewById(R.id.textViewHeightaccO);
        mDecraccText = (TextView)findViewById(R.id.textViewDecraccO);
        mTimeaccText = (TextView)findViewById(R.id.textViewTimeaccO);
        mStepText = (TextView)findViewById(R.id.textViewStepO);
        mCalibrateIn = (EditText)findViewById(R.id.editTextHeightcal);
        mStartButton = (Button)findViewById(R.id.buttonStart);
        mStepDailyText = (TextView)findViewById(R.id.textViewDailyStepsNum);
        mHeightDailyText = (TextView)findViewById(R.id.textViewDailyHeightNum);
        mStepDailyProgress = (ProgressBar)findViewById(R.id.progressBarSteps);
        mStepDailyProgress.setMax(100);
        mHeightDailyProgress = (ProgressBar)findViewById(R.id.progressBarHeight);
        mHeightDailyProgress.setMax(100);
        mFloatingButton = (FloatingActionButton) findViewById(R.id.fab);


        // initialize start button
        mStartButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                startLogger();
            }
        });


        /*
        Create an "Intentfilter" - this is necessary to receive the correct messages in
        the Broadcastreceiver (MyReceiver), which is registered afterwards with this filter.
        On sender-side the intent for broadcast must have an action with the same string as the
        filter (here: grmpl.mk.custom.intent.Callback)
        (Please note: Only the string is important for connecting sender and receiver.
          The string is only a string and freely selectable but must be unique.)
         */
        IntentFilter filter = new IntentFilter();
        filter.addAction("grmpl.mk.stepandheighcounter.custom.intent.Callback");
        mReceiver = new MyReceiver();
        this.registerReceiver(mReceiver, filter);

        // Start my service - multiple calls of start are no problem, they don't start the
        //  service again, they just call the onStartCommand method of the service
        //   ("start" is just logical activating and ressource allocation - code is only run when event is happening!!)
        startService(new Intent(this, SensorService.class));
        // bind to it
        bindService(new Intent(this,
                SensorService.class), mConnection, Context.BIND_AUTO_CREATE);


        if (mSettings.getBoolean(cPREF_DEBUG,false))
            Toast.makeText(MainActivity.this, R.string.debug_create_finished, Toast.LENGTH_SHORT).show();
        // we need access to SD-Card
        if (mSettings.getBoolean("mReqSDPermission",true))
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);

    }

    ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            if (mSettings.getBoolean(cPREF_DEBUG,false))
                Toast.makeText(MainActivity.this, R.string.debug_service_disconnected, Toast.LENGTH_SHORT).show();
            mBounded = false;
            mSensService = null;
        }
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mSettings.getBoolean(cPREF_DEBUG,false))
                Toast.makeText(MainActivity.this, R.string.debug_service_connected, Toast.LENGTH_SHORT).show();
            mBounded = true;
            LocalBinder mLocalBinder = (LocalBinder)service;
            mSensService = mLocalBinder.getServerInstance();
            mSensService.getValues();
            getHeightRegulary();
        }
    };


    @Override
    public void onResume() {
        super.onResume();
        // Todo: Rework necessary if service will be stopped
        //        if service is stopped, we don't get actual values
        // get actual values
        if(mSensService != null ) {
            mSensService.getValues();
        }


        // permissions and settings could change during pause
        mSave = new SaveData(this);
        if (!(new CheckSDCard(this).checkWriteSDCard())) {
            mFloatingButton.setVisibility(View.VISIBLE);
            Toast.makeText(MainActivity.this, R.string.cant_write_sdcard, Toast.LENGTH_LONG).show();

            mFloatingButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Snackbar.make(view, R.string.cant_write_sdcard, Snackbar.LENGTH_LONG).show();
                }
            });
        }
        else if (getDetailSave("m")) {
            mFloatingButton.setVisibility(View.VISIBLE);
            mFloatingButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    float height;
                    Snackbar.make(view, R.string.saving_marker, Snackbar.LENGTH_LONG).show();
                    if (mSensService != null) height = mSensService.getHeight();
                    else height = -9996;
                    mSave.saveStatistics(System.currentTimeMillis(), mSettings.getFloat("mStepsCumul0", 0),
                            mSettings.getFloat("mHeightCumul0", 0), mSettings.getFloat("mDecrCumul0", 0),
                            mSettings.getFloat("mTimeCumul0", 0), height, cSTAT_TYPE_MARK);
                }
            });
        }
        else mFloatingButton.setVisibility(View.GONE);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                mStartButton.requestFocus();
                mStartButton.requestFocusFromTouch();
            }
        });


    }

    // Only unbind, if destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSensService != null){
            unbindService(mConnection);
            unregisterReceiver(mReceiver);
            mBounded = false;
        }
    }



    // action for start button: starting measurement
    private void startLogger() {
        boolean succ = mSensService.startListeners();
        if(succ && mSettings.getBoolean(cPREF_DEBUG,false))
            Toast.makeText(MainActivity.this, R.string.debug_listener_started, Toast.LENGTH_SHORT).show();
        else mStatusText.setText(R.string.sensor_register_failed); //Reregistering when already running will give an error, too!
    }

    // action for stop button: stopping measurement
    private void stopLogger() {
        /*
           Todo: Service could be stopped completely. Keeping service alive is a relict
                  from previous versions where I didn't have implemented the persistency.
                  Stopping of service would free memory ressources. But I have some features
                  which depend on running service even if no measurement is running.
                  (e.g. height calibration and reset)
         */

        /*
          Unregister sensors and save actual steps to evaluate pause steps later
        */
        if (mSensService != null) mSensService.stopListeners();
        if(mSettings.getBoolean(cPREF_DEBUG,false)) mStatusText.setText(R.string.sensor_pause);
    }

    // action for reset button: reset values
    public void resetData(View view) {
        // Todo: Rework necessary if service will be stopped
        //        at the moment reset can be done even without service
        if (mSensService != null) mSensService.resetData();
        mStepText.setText(R.string.zero);
        mHeightText.setText(R.string.height_init1);
        mHeightaccText.setText(R.string.zero_m);
        mDecraccText.setText(R.string.zero_m);
        mTimeaccText.setText(R.string.zero_s);
        mStatusText.setText("");
        mCalibrateIn.setText("");
        mCalibrateIn.setHint(R.string.height_m);
    }


    // action for calibration button: calibrate height
    public void calibrateHeight(View view){
        // Todo: Rework necessary if service will be stopped
        //      Currently calibration is possible even if measurement is not started yet
        //       but we need a running service for this
        if (mSensService != null) {
            float height;
            try {
                height = Float.parseFloat(mCalibrateIn.getText().toString());
            } catch (NumberFormatException nfe) {
                mStatusText.setText(R.string.calib_not_number);
                return;
            }
            //call Service calibrate
            mSensService.calibrateHeight(height);
            mHeightText.setText(String.format(Locale.getDefault(), "%.1f m", height));
        }
        else if (mSettings.getBoolean(cPREF_DEBUG, false))
                Toast.makeText(MainActivity.this, R.string.service_not_started, Toast.LENGTH_LONG).show();

        // Closing keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // as long as service is running, we update height information regulary
    private void getHeightRegulary(){
        if (mSensService != null) {
            mHeightText.setText(String.format(Locale.getDefault(), "%.1f m", mSensService.getHeight()));
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(this::getHeightRegulary, cINTERVAL_UPDATE_HEIGHT);
            /* Same as:
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    getHeightRegulary();
                }
            }, cINTERVAL_UPDATE_HEIGHT);
            */
        }
    }


    // For callback from service
    /* Just create a class which extends Broadcastreceiver.
       Service will make a Broadcast and with "Intentfilter" it is assured, that only
       wanted messages are received.
       We register it dynamically so we can use a non-static nested class
     */
    public class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent receive){
            // set all output fields
            String outtext = receive.getStringExtra("Status");
            mStatusText.setText(outtext);
            Float steps = receive.getFloatExtra("Steps",0F);
            mStepText.setText(String.format(Locale.getDefault(),"%.0f",steps));
            Float height = receive.getFloatExtra("Height",997F);
            mHeightText.setText(String.format(Locale.getDefault(),"%.1f m",height));
            Float heightacc = receive.getFloatExtra("Heightacc",0F);
            mHeightaccText.setText(String.format(Locale.getDefault(),"%.1f m",heightacc));
            Float decracc = receive.getFloatExtra("Decracc",0F);
            mDecraccText.setText(String.format(Locale.getDefault(),"%.1f m",decracc));
            Long timeacc = (long)receive.getFloatExtra("Timeacc",0F);
            // mTimeaccText.setText(String.format(Locale.getDefault(),"%.1f s",timeacc));
            mTimeaccText.setText(String.format(Locale.getDefault(),"%02d:%02d:%02d",
                    TimeUnit.SECONDS.toHours(timeacc),
                    TimeUnit.SECONDS.toMinutes(timeacc),
                    TimeUnit.SECONDS.toSeconds(timeacc)
            ));
            float stepstoday = receive.getFloatExtra("Stepstoday",0F);
            mStepDailyText.setText(String.format(Locale.getDefault(),"%.0f",stepstoday));
            // set progress bars
            int dailysteps = Integer.parseInt(Objects.requireNonNull(mSettings.getString(cPREF_TARGET_STEPS, "100000")));
            // dailysteps can be set to zero, we must avoid division by zero when stepstoday is wrongly set to negative
            if (stepstoday < dailysteps  && stepstoday >= 0) {
                // difficult to read, bar color sufficient: mStepDailyText.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
                mStepDailyProgress.setProgress( (int)(100 * stepstoday) / dailysteps );
                mStepDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorAccent)));
            }
            // Target reached
            else if (stepstoday >= dailysteps) {
                // difficult to read, bar color sufficient: mStepDailyText.setTextColor(ContextCompat.getColor(context,R.color.colorPrimaryDark));
                mStepDailyProgress.setProgress( 100 );
                mStepDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorPrimaryDark)));
            }
            // anything other (e.g. no measurement yet
            else {
                mStepDailyProgress.setProgress( 0 );
                mStepDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorPrimaryDark)));
            }
            float heighttoday = receive.getFloatExtra("Heighttoday",0F);
            mHeightDailyText.setText(String.format(Locale.getDefault(),"%.1f m",heighttoday));
            int dailyheight = Integer.parseInt(Objects.requireNonNull(mSettings.getString(cPREF_TARGET_HEIGHT, "100")));
            // see above
            if (heighttoday < dailyheight && heighttoday >=0 ) {
                // difficult to read, bar color sufficient: mHeightDailyText.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
                mHeightDailyProgress.setProgress( (int)(100 * heighttoday) / dailyheight );
                mHeightDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorAccent)));
            }
            else if (heighttoday >= dailyheight){
                // difficult to read, bar color sufficient: mHeightDailyText.setTextColor(ContextCompat.getColor(context,R.color.colorPrimaryDark));
                mHeightDailyProgress.setProgress( 100 );
                mHeightDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorPrimaryDark)));
            }
            else {
                mHeightDailyProgress.setProgress( 0 );
                mHeightDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorPrimaryDark)));
            }

            // set Start/Stop-Button
            mRunning = receive.getBooleanExtra("Registered",false);

            if(mRunning){
                mStartButton.setText(R.string.button_running);
                mStartButton.setOnClickListener(new View.OnClickListener(){
                    public void onClick(View v){
                        stopLogger();
                    }
                });
            } else {
                mStartButton.setText(R.string.button_pause);
                mStartButton.setOnClickListener(new View.OnClickListener(){
                    public void onClick(View v){
                        startLogger();
                    }
                });
            }
        }

    }

    // checking what values have to be saved in detail statistics
    private boolean getDetailSave(String identifier){
        Set<String> detail_multi = mSettings.getStringSet(cPREF_STAT_DETAIL_MULTI, cPREF_STAT_DETAIL_MULTI_DEFAULT);

        boolean ret = false;
        assert detail_multi != null;
        for (String s:  detail_multi ) {
            ret = ret || s.equals(identifier);
        }
        return ret;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent it = new Intent(this, SettingsActivity.class);
            //startActivityForResult(it,1);
            startActivity(it);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

