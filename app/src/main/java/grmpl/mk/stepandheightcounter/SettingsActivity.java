package grmpl.mk.stepandheightcounter;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.widget.Toast;

import static grmpl.mk.stepandheightcounter.Constants.*;

public class SettingsActivity extends PreferenceActivity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();

    }

    public static class MyPreferenceFragment extends PreferenceFragment
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            // disable settings if there is no write access to SDCard
            if (!(new CheckSDCard(getActivity()).checkWriteSDCard())) {
                // Disable all write settings
                PreferenceGroup preferenceGroup = (PreferenceGroup) findPreference("pref_cat_stat");
                preferenceGroup.setEnabled(false);
                preferenceGroup = (PreferenceGroup) findPreference("pref_cat_stat");
                preferenceGroup.setEnabled(false);
                Toast.makeText(getActivity(), R.string.no_write_sdcard_settings_disbled, Toast.LENGTH_LONG).show();
            }


            // prevent setting 0 for number of files to keep
            //  OnPreferenceChangeListener is called before setting is saved and will prevent saving
            //    if return code is false.
            Preference.OnPreferenceChangeListener changelistener =  new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    //  Value of 0 will not be accepted for max number of files
                    return !( (preference.getKey().equals(cPREF_STAT_HOUR_CLEAR_NUM) || preference.getKey().equals(cPREF_STAT_DETAIL_CLEAR_NUM) )
                            && Integer.valueOf(value.toString()) < 1 );

                }
            };
            findPreference(cPREF_STAT_HOUR_CLEAR_NUM).setOnPreferenceChangeListener(changelistener);
            findPreference(cPREF_STAT_DETAIL_CLEAR_NUM).setOnPreferenceChangeListener(changelistener);
        }
    }
}