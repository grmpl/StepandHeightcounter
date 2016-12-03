package grmpl.mk.stepandheightcounter;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity {

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
            if (!(new CheckSDCard(getActivity()).checkWriteSDCard())) {
                // Disable all write settings
                PreferenceGroup preferenceGroup = (PreferenceGroup) findPreference("pref_cat_stat");
                preferenceGroup.setEnabled(false);
                preferenceGroup = (PreferenceGroup) findPreference("pref_cat_stat");
                preferenceGroup.setEnabled(false);
                Toast.makeText(getActivity(), R.string.no_write_sdcard_settings_disbled, Toast.LENGTH_LONG).show();
            }
        }
    }

}