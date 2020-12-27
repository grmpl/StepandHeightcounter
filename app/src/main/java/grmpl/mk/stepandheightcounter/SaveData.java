package grmpl.mk.stepandheightcounter;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static grmpl.mk.stepandheightcounter.Constants.*;


class SaveData {

    // variable to hold context
    private Context mContext;
    private CheckSDCard mCheckSDCard;

    // Constructor
    SaveData(Context context) {
        mContext = context;
        mCheckSDCard = new CheckSDCard(mContext);
    }


    // saving debug information if activated
    //  check for activation is done here, otherwise we need to implement SharedPreferences in AlarmReceiver, too
    void saveDebugStatus(String outline) {
        if(mCheckSDCard.checkWriteSDCard()){
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
            if (settings.getBoolean(cPREF_DEBUG, false)) {
                SimpleDateFormat sdformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                Date actualdate = new Date(System.currentTimeMillis());
                File directory = getDirectory();

                if (!directory.exists()) {
                    if (!directory.mkdirs()) {
                        return;
                    }
                }

                String filename = "debug_status.txt";
                FileOutputStream out = null;

                outline = sdformat.format(actualdate) + ": " + outline + "\n";

                try {
                    out = new FileOutputStream(directory + File.separator + filename, true);
                    out.write(outline.getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        assert out != null;
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }

    }

    // saving detailed information for debugging purposes
    //  (check for activation is done in call, to avoid unnecessary method call)
    void saveDebugValues(String valueline) {
        /* Debugging:
        saveDebugStatus(dStr.toString());
        */
        if(mCheckSDCard.checkWriteSDCard()) {

            //Save data to file
            SimpleDateFormat sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
            Date actualdate = new Date(System.currentTimeMillis());
            File directory = getDirectory();
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    return;
                }
            }

            String filename = sdformat.format(actualdate) + "_debug.txt";
            File file = new File(directory + File.separator + filename);
            FileOutputStream out = null;

            try {
                String outline;
                if (!file.exists()) {
                    out = new FileOutputStream(file);
                    outline = "system time (correlation); step event time; step event timestamp; steps; stepstotal; pressure time; pressure; height; reference height; reference height timestamp; accumulated height\n";
                    out.write(outline.getBytes());
                    out.write(valueline.getBytes());
                    out.close();
                } else {
                    out = new FileOutputStream(file, true);
                    out.write(valueline.getBytes());
                }

                out.close();
            } catch (Exception e) {
                saveDebugStatus(e.getMessage());
            } finally {
                try {
                    assert out != null;
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    // Saving statistics - one method with height information, one without
    void saveStatistics(long timems, float stepscumul, float heightcumul, float decrcumul, float timecumul, float height, String type) {
        saveStatistics(timems, stepscumul, heightcumul, decrcumul, timecumul, height, type, true);
    }

    void saveStatistics(long timems, float stepscumul, float heightcumul, float timecumul, float decrcumul, String type) {
        saveStatistics(timems, stepscumul, heightcumul, decrcumul, timecumul, -99997, type, false);
    }

    private void saveStatistics(long timems, float stepscumul, float heightcumul, float decrcumul, float timecumul, float height, String type, boolean heightout) {
        if(mCheckSDCard.checkWriteSDCard()) {
            SimpleDateFormat sdformat, sdformati;
            final String filenamet;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
            boolean autoclean;
            int keep;

            //Save data to file
            switch (type) {
                // For daily statistics
                case cSTAT_TYPE_DAILY:
                    sdformat = new SimpleDateFormat("yyyy", Locale.US);
                    sdformati = new SimpleDateFormat("yyyy-MM-dd, zzz", Locale.US); // fixed formatting, not local formatting, day is enough
                    autoclean = false;
                    keep = 9999;
                    break;
                // for regular statistics
                case cSTAT_TYPE_REGULAR:
                    sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                    sdformati = new SimpleDateFormat("yyyy-MM-dd HH:mm, zzz", Locale.US); //seconds not needed
                    autoclean = settings.getBoolean(cPREF_STAT_HOUR_CLEAR, false);
                    keep = Integer.parseInt(Objects.requireNonNull(settings.getString(cPREF_STAT_HOUR_CLEAR_NUM, "9999")));
                    // to save the last interval not at next day 0:00, but on this day 24:00 we adjust the time a little bit
                    timems = timems - 1;
                    break;
                // for detailed statistics
                default:
                    sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                    sdformati = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss, zzz", Locale.US); // fixed formatting, not local formatting
                    autoclean = settings.getBoolean(cPREF_STAT_DETAIL_CLEAR, false);
                    keep = Integer.parseInt(Objects.requireNonNull(settings.getString(cPREF_STAT_DETAIL_CLEAR_NUM, "9999")));
            }

            filenamet = getFilenameEnding(type);
            File directory = getDirectory();

            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    return;
                }
            }

            File file = new File(directory + File.separator + sdformat.format(timems) + filenamet);
            FileOutputStream out = null;
            // We choose "," as separator, as this seems to be more common than ";"
            // We take back the adjustment of regular intervals from above to get proper timestamps ( 0:59 looks ugly)
            //  for daily statistics (0:00) and detail statistics (actual timestamp) 1 msec doesn't make a difference
            String outline = sdformati.format(timems+1) + ",";
            // For the last interval of the day we must make adjustments
            if (outline.regionMatches(11,"00:00",0,5))
                outline = (sdformati.format(timems) + ",").replace("23:59","24:00");
            if (heightout)
                outline = outline
                        + String.format(Locale.US, "%.0f, %.0f, %.0f, %.0f, %.0f, ", stepscumul, heightcumul, decrcumul, timecumul, height)
                        + type + "\n";
            else
                outline = outline
                        + String.format(Locale.US, "%.0f, %.0f, %.0f, ", stepscumul, heightcumul, decrcumul, timecumul)
                        + type + "\n";

            try {
                String headline;
                if (!file.exists()) {
                    // Create a new file with header
                    out = new FileOutputStream(file);
                    if (heightout) headline = mContext.getString(R.string.save_stat_headline_height) + "\n";
                    else headline = mContext.getString(R.string.save_stat_headline) + "\n";
                    out.write(headline.getBytes());
                    out.write(outline.getBytes());
                    out.close();

                    // Delete old files
                    if (autoclean) {
                        deleteFiles(type,keep);
                    }
                } else {
                    out = new FileOutputStream(file, true);
                    out.write(outline.getBytes());
                }

                out.close();
            } catch (Exception e) {
                saveDebugStatus(e.getMessage());
            } finally {
                try {
                    assert out != null;
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // If AlertDialog was ended with "Yes", we set the Checkbox for Autoclean and delete old files
    void saveClearOnAndDelete(String type, PreferenceFragmentCompat fragment){
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
        int keep;
        switch (type) {
            // For daily statistics - shouldn't happen!
            case cSTAT_TYPE_DAILY:
                saveDebugStatus("Wrong type");
                break;
            // for regular statistics
            case cSTAT_TYPE_REGULAR:
                keep = Integer.parseInt(Objects.requireNonNull(settings.getString(cPREF_STAT_HOUR_CLEAR_NUM, "9999")));
                // We got the fragment, so we can change the preference directly
                CheckBoxPreference box1 = fragment.findPreference(cPREF_STAT_HOUR_CLEAR);
                assert box1 != null;
                box1.setChecked(true);
                deleteFiles(type, keep);
                break;
            // for detailed statistics
            default:
                keep = Integer.parseInt(Objects.requireNonNull(settings.getString(cPREF_STAT_DETAIL_CLEAR_NUM, "9999")));
                // We got the fragment, so we can change the preference directly
                CheckBoxPreference box2 = fragment.findPreference(cPREF_STAT_DETAIL_CLEAR);
                assert box2 != null;
                box2.setChecked(true);
                deleteFiles(type, keep);
        }

    }

    // If AlertDialog was ended with "Yes", we set the number for Autoclean as choosen and delete old files
    void saveNumberAndDelete(String type, int number, PreferenceFragmentCompat fragment){
        switch (type) {
            // For daily statistics
            case cSTAT_TYPE_DAILY:
                saveDebugStatus("Wrong type");
                break;
            // for regular statistics
            case cSTAT_TYPE_REGULAR:
                // We got the fragment, so we can change the preference directly
                EditTextPreference text1 = fragment.findPreference(cPREF_STAT_HOUR_CLEAR_NUM);
                assert text1 != null;
                text1.setText(Integer.toString(number));
                deleteFiles(type,number);
                break;
            // for detailed statistics
            default:
                // We got the fragment, so we can change the preference directly
                EditTextPreference text2 = fragment.findPreference(cPREF_STAT_DETAIL_CLEAR_NUM);
                assert text2 != null;
                text2.setText(Integer.toString(number));
                deleteFiles(type, number);
        }

    }


    // deleting old files
    private void deleteFiles(String type, int keep){
        File directory = getDirectory();
        if (!directory.exists()) return;
        final String filenameend = getFilenameEnding(type);

        // Always keep one file, even if check in settings went wrong
        if (keep < 1) keep = 1;

        File[] filelist = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.contains(filenameend);
            }
        });
        assert filelist != null;
        int deletefiles = filelist.length - keep;
        if (deletefiles > 0) {
            Arrays.sort(filelist);
            for (int i = 0; i < deletefiles; i++) {
                if ( !filelist[i].delete() ) saveDebugStatus("File delete failed.");
            }
        }
    }

    // central point for directory
    private File getDirectory(){
        String temp =  Objects.requireNonNull(mContext.getExternalFilesDir(cDIRECTORY)).toString();// + File.separator + cDIRECTORY;
        return new File(temp);
    }

    private String getFilenameEnding(String type){
        switch (type) {
            // For daily statistics
            case cSTAT_TYPE_DAILY:
                return cFILENAME_STAT_DAILY;
            // for regular statistics
            case cSTAT_TYPE_REGULAR:
                return cFILENAME_STAT_REG;
            // for detailed statistics
            default:
                return cFILENAME_STAT_DETAIL;
        }

    }

}