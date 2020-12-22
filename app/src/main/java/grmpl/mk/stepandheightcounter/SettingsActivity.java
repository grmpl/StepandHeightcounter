package grmpl.mk.stepandheightcounter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import android.widget.Toast;

import java.util.Objects;

import static grmpl.mk.stepandheightcounter.Constants.*;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new MyPreferenceFragment())
                .commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat
    {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            final PreferenceFragmentCompat mFragment = this;

            // disable settings if there is no write access to SDCard
            if (!(new CheckSDCard(getActivity()).checkWriteSDCard())) {
                // Disable all write settings
                PreferenceGroup preferenceGroup = findPreference("pref_cat_stat");
                assert preferenceGroup != null;
                preferenceGroup.setEnabled(false);
                Toast.makeText(getActivity(), R.string.no_write_sdcard_settings_disbled, Toast.LENGTH_LONG).show();
            }


            // prevent setting 0 for number of files to keep
            //  OnPreferenceChangeListener is called before setting is saved and will prevent saving
            //    if return code is false.
            Preference.OnPreferenceChangeListener changelistenernum =  new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(requireActivity());
                    String key = preference.getKey();
                    final int newnumber = Integer.parseInt(value.toString());
                    final String type;
                    boolean autocleanon = true;
                    switch (key) {
                        case cPREF_STAT_DETAIL_CLEAR_NUM:
                            type = cSTAT_TYPE_SENS;
                            autocleanon = settings.getBoolean(cPREF_STAT_DETAIL_CLEAR,true);
                            break;
                        case cPREF_STAT_HOUR_CLEAR_NUM:
                            type = cSTAT_TYPE_REGULAR;
                            autocleanon = settings.getBoolean(cPREF_STAT_HOUR_CLEAR,true);
                            break;
                        default:
                            type = cSTAT_TYPE_DAILY;
                    }
                    //  Value of 0 will not be accepted for max number of files
                    if ( Integer.parseInt(value.toString()) < 1 ) {
                        new AlertDialog.Builder(getActivity())
                                .setTitle(R.string.alert_title_zero_num)
                                .setMessage(R.string.alert_message_zero_num)
                                .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // do nothing
                                    }
                                }).create().show();
                        return false;
                    }
                    // if value is smaller than before and autoclean on, make AlertDialog and delete files
                    else if ( Integer.parseInt(Objects.requireNonNull(settings.getString(key, "999"))) > newnumber && autocleanon){
                        new AlertDialog.Builder(getActivity())
                                .setTitle(R.string.alert_title_autoclean_change)
                                .setMessage(R.string.alert_message_autoclean_changed)
                                .setNegativeButton(R.string.No, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // do nothing
                                    }
                                })
                                .setPositiveButton(R.string.Yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // OK has been pressed => set the new value and delete files
                                        SaveData msave = new SaveData(getActivity());
                                        msave.saveNumberAndDelete(type, newnumber, mFragment);
                                    }
                                }).create().show();
                        // We don't set the new value now, but at the onClick-method
                        return false;
                    }
                    else return true;
                }
            };
            findPreference(cPREF_STAT_HOUR_CLEAR_NUM).setOnPreferenceChangeListener(changelistenernum);
            findPreference(cPREF_STAT_DETAIL_CLEAR_NUM).setOnPreferenceChangeListener(changelistenernum);

            Preference.OnPreferenceChangeListener changelistenerclearon =  new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    if (value.equals(true)) {
                        final String type;
                        switch (preference.getKey()) {
                            case cPREF_STAT_DETAIL_CLEAR:
                                type = cSTAT_TYPE_SENS;
                                break;
                            case cPREF_STAT_HOUR_CLEAR:
                                type = cSTAT_TYPE_REGULAR;
                                break;
                            default:
                                type = cSTAT_TYPE_DAILY;
                        }

                        new AlertDialog.Builder(getActivity())
                                .setTitle(R.string.alert_title_autoclean_change)
                                .setMessage(R.string.alert_message_autoclean_changed)
                                .setNegativeButton(R.string.No, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // do nothing
                                    }
                                })
                                .setPositiveButton(R.string.Yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // OK has been pressed => set the new value and delete files
                                        SaveData msave = new SaveData(getActivity());
                                        msave.saveClearOnAndDelete(type, mFragment);
                                    }
                                }).create().show();
                        return false;
                    }
                    return true;

                }
            };
            findPreference(cPREF_STAT_DETAIL_CLEAR).setOnPreferenceChangeListener(changelistenerclearon);
            findPreference(cPREF_STAT_HOUR_CLEAR).setOnPreferenceChangeListener(changelistenerclearon);

        }
    }
}