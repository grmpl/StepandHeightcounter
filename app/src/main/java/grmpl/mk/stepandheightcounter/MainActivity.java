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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
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
import java.util.Set;

import grmpl.mk.stepandheightcounter.SensorService.LocalBinder;

import static grmpl.mk.stepandheightcounter.Constants.*;


public class MainActivity extends AppCompatActivity {

    private TextView mStatusText, mHeightText, mStepText, mHeightaccText, mStepDailyText, mHeightDailyText;
    private EditText mCalibrateIn;
    private Button mStartButton;
    private FloatingActionButton mFloatingButton;

    private ProgressBar mStepDailyProgress, mHeightDailyProgress;
    boolean mBounded = false, mRunning = false;
    SensorService mSensService;
    MyReceiver mReceiver = null;
    SharedPreferences mSettings;
    SaveData mSave;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Access to persistent data
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        // Get all necessary elements
        mStatusText = (TextView)findViewById(R.id.textViewError);
        mHeightText = (TextView)findViewById(R.id.textViewHeightO);
        mHeightaccText = (TextView)findViewById(R.id.textViewHeightaccO);
        mStepText = (TextView)findViewById(R.id.textViewStepO);
        mStartButton = (Button)findViewById(R.id.buttonStart);
        mCalibrateIn = (EditText)findViewById(R.id.editTextHeightcal);
        mStartButton = (Button)findViewById(R.id.buttonStart);
        mStepDailyText = (TextView)findViewById(R.id.textViewDailyStepsNum);
        mHeightDailyText = (TextView)findViewById(R.id.textViewDailyHeightNum);
        mStepDailyProgress = (ProgressBar)findViewById(R.id.progressBarSteps);
        mHeightDailyProgress = (ProgressBar)findViewById(R.id.progressBarHeight);
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
                            mSettings.getFloat("mHeightCumul0", 0), height, cSTAT_TYPE_MARK);
                }
            });
        }
        else mFloatingButton.setVisibility(View.GONE);
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
    public void startLogger() {
        boolean succ = mSensService.startListeners();
        if(succ && mSettings.getBoolean(cPREF_DEBUG,false))
            Toast.makeText(MainActivity.this, R.string.debug_listener_started, Toast.LENGTH_SHORT).show();
        else mStatusText.setText(R.string.sensor_register_failed); //Reregistering when already running will give an error, too!
    }

    // action for stop button: stopping measurement
    public void stopLogger() {
        /*
           Todo: Service could be stopped completely. Keeping service alive is a relict from
                  previous versions where I didn't have implemented the persistency.
                  Rework has to be done carefully, I don't know where I anticipate a running service.
         */

        /*
          Unregister sensors and save actual steps to evaluate pause steps later
        */
        if (mSensService != null) mSensService.stopListeners();
        if(mSettings.getBoolean(cPREF_DEBUG,false)) mStatusText.setText(R.string.sensor_pause);
    }

    // action for reset button: reset values
    public void resetData(View view) {
        if (mSensService != null) mSensService.resetData();
        mStepText.setText(R.string.zero);
        mHeightText.setText(R.string.height_init1);
        mHeightaccText.setText(R.string.zero_m);
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

        // I don't remember why this is necessary:
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // as long as service is running, we update height information regulary
    void getHeightRegulary(){
        if (mSensService != null) {
            mHeightText.setText(String.format(Locale.getDefault(), "%.1f m", mSensService.getHeight()));
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    getHeightRegulary();
                }
            }, cINTERVAL_UPDATE_HEIGHT);
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
            String outtext = receive.getStringExtra("Status");
            mStatusText.setText(outtext);
            Float steps = receive.getFloatExtra("Steps",0F);
            mStepText.setText(String.format(Locale.getDefault(),"%.0f",steps));
            Float height = receive.getFloatExtra("Height",997F);
            mHeightText.setText(String.format(Locale.getDefault(),"%.1f m",height));
            Float heightacc = receive.getFloatExtra("Heightacc",0F);
            mHeightaccText.setText(String.format(Locale.getDefault(),"%.1f m",heightacc));
            Float stepstoday = receive.getFloatExtra("Stepstoday",0F);
            mStepDailyText.setText(String.format(Locale.getDefault(),"%.0f",stepstoday));
            int dailysteps = Integer.valueOf(mSettings.getString(cPREF_TARGET_STEPS, "100000"));
            if (stepstoday < dailysteps) {
                // difficult to read, bar color sufficient: mStepDailyText.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
                mStepDailyProgress.setProgress( (int)(100 * stepstoday) / dailysteps );
                mStepDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorAccent)));
            }
            else {
                // difficult to read, bar color sufficient: mStepDailyText.setTextColor(ContextCompat.getColor(context,R.color.colorPrimaryDark));
                mStepDailyProgress.setProgress( 100 );
                mStepDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorPrimaryDark)));
            }
            Float heighttoday = receive.getFloatExtra("Heighttoday",0F);
            mHeightDailyText.setText(String.format(Locale.getDefault(),"%.1f m",heighttoday));
            int dailyheight = Integer.valueOf(mSettings.getString(cPREF_TARGET_HEIGHT, "100"));
            if (heighttoday < dailyheight) {
                // difficult to read, bar color sufficient: mHeightDailyText.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
                mHeightDailyProgress.setProgress( (int)(100 * heighttoday) / dailyheight );
                mHeightDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorAccent)));
            }
            else {
                // difficult to read, bar color sufficient: mHeightDailyText.setTextColor(ContextCompat.getColor(context,R.color.colorPrimaryDark));
                mHeightDailyProgress.setProgress( 100 );
                mHeightDailyProgress.setProgressTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context,R.color.colorPrimaryDark)));
            }
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

    // this is the callback if permission dialog is ended - we save the result
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, R.string.permission_sdcard_granted,
                            Toast.LENGTH_SHORT).show();
                    SharedPreferences.Editor editpref =
                            PreferenceManager.getDefaultSharedPreferences(this).edit();
                    editpref.putBoolean("mReqSDPermission",false);
                    editpref.apply();

                } else {
                    Toast.makeText(MainActivity.this, R.string.cant_write_sdcard,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // checking what values have to be saved in detail statistics
    boolean getDetailSave(String identifier){
        Set<String> detail_multi = mSettings.getStringSet(cPREF_STAT_DETAIL_MULTI, cPREF_STAT_DETAIL_MULTI_DEFAULT);

        boolean ret = false;
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

