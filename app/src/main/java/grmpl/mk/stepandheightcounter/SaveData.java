package grmpl.mk.stepandheightcounter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

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
                String directoryname = Environment.getExternalStorageDirectory() + File.separator + cDIRECTORY;
                File directory = new File(directoryname);

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
    int saveDebugValues(String valueline) {
        /* Debugging:
        saveDebugStatus(dStr.toString());
        */
        if(mCheckSDCard.checkWriteSDCard()) {

            //Save data to file
            SimpleDateFormat sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
            Date actualdate = new Date(System.currentTimeMillis());
            String directoryname = Environment.getExternalStorageDirectory() + File.separator + cDIRECTORY;


            File directory = new File(directoryname);
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    return -1;
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
                return 0;
            } catch (Exception e) {
                saveDebugStatus(e.getMessage());
                return -2;
            } finally {
                try {
                    assert out != null;
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else return -3;
    }


    // Saving statistics - one method with height information, one without
    int saveStatistics(long timems, float stepscumul, float heightcumul, float height, String type) {
        return saveStatistics(timems,stepscumul,heightcumul,height,type,true);
    }

    int saveStatistics(long timems, float stepscumul, float heightcumul, String type) {
        return saveStatistics(timems,stepscumul,heightcumul, -9997, type, false);
    }

    private int saveStatistics(long timems, float stepscumul, float heightcumul, float height, String type, boolean heightout) {
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
                    filenamet = cFILENAME_STAT_DAILY;
                    autoclean = false;
                    keep = 9999;
                    break;
                // for regular statistics
                case cSTAT_TYPE_REGULAR:
                    sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                    sdformati = new SimpleDateFormat("yyyy-MM-dd HH:mm, zzz", Locale.US); //seconds not needed
                    filenamet = cFILENAME_STAT_REG;
                    autoclean = settings.getBoolean(cPREF_STAT_HOUR_CLEAR, false);
                    keep = Integer.valueOf(
                            settings.getString(cPREF_STAT_HOUR_CLEAR_NUM, "9999"));
                    // to save the last interval not at next day 0:00, but on this day 24:00 we adjust the time a little bit
                    timems = timems - 1;
                    break;
                // for detailed statistics
                default:
                    sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                    sdformati = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss, zzz", Locale.US); // fixed formatting, not local formatting
                    filenamet = cFILENAME_STAT_DETAIL;
                    autoclean = settings.getBoolean(cPREF_STAT_DETAIL_CLEAR, false);
                    keep = Integer.valueOf(
                            settings.getString(cPREF_STAT_DETAIL_CLEAR_NUM, "9999"));
            }

            // Always keep one file, even if check in settings went wrong
            if (keep < 1) keep = 1;

            String directoryname = Environment.getExternalStorageDirectory() + File.separator + cDIRECTORY;


            File directory = new File(directoryname);
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    return -1;
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
                        + String.format(Locale.US, "%.0f, %.0f, %.0f, ", stepscumul, heightcumul, height)
                        + type + "\n";
            else
                outline = outline
                        + String.format(Locale.US, "%.0f, %.0f, ", stepscumul, heightcumul)
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
                        File[] filelist = directory.listFiles(new FilenameFilter() {
                            @Override
                            public boolean accept(File file, String s) {
                                return s.contains(filenamet);
                            }
                        });
                        int deletefiles = filelist.length - keep;
                        if (deletefiles > 0) {
                            Arrays.sort(filelist);
                            for (int i = 0; i < deletefiles; i++) {
                                if (!filelist[i].delete() ) saveDebugStatus("File delete failed.");
                            }
                        }
                    }
                } else {
                    out = new FileOutputStream(file, true);
                    out.write(outline.getBytes());
                }

                out.close();
                return 0;
            } catch (Exception e) {
                saveDebugStatus(e.getMessage());
                return -2;
            } finally {
                try {
                    assert out != null;
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else return -3;
    }

}