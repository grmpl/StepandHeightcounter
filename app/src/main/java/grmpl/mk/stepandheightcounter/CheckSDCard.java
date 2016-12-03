package grmpl.mk.stepandheightcounter;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;


class CheckSDCard {

    // variable to hold context
    private Context mContext;

    CheckSDCard(Context context) {
        mContext = context;
    }

    boolean checkWriteSDCard() {
        boolean mExternalStorageWriteable = false;
        String state = Environment.getExternalStorageState();

        if (ActivityCompat.checkSelfPermission(mContext,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED )
            // if mounted rw this is true, else this will be false
            mExternalStorageWriteable = Environment.MEDIA_MOUNTED.equals(state);
        else {
            // doesn't work if called from service!
            // ActivityCompat.requestPermissions((Activity) mContext, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
            // so, we use sharedpref
            SharedPreferences.Editor editpref = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
            editpref.putBoolean("mReqSDPermission",true);
            editpref.apply();
        }

        return mExternalStorageWriteable;
    }
}
