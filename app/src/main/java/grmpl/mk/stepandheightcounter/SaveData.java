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

    SaveData(Context context) {
        mContext = context;
        mCheckSDCard = new CheckSDCard(mContext);
    }


    void saveDebugStatus(String outline) {
        if(mCheckSDCard.checkWriteSDCard()){
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
            if (settings.getBoolean(mPREF_DEBUG, false)) {
                SimpleDateFormat sdformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                Date actualdate = new Date(System.currentTimeMillis());
                String directoryname = Environment.getExternalStorageDirectory() + File.separator + mDIRECTORY;
                File directory = new File(directoryname);

                if (!directory.exists()) {
                    if (!directory.mkdirs()) {
                        return;
                    }
                }

                String filename = "status.out";
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

    int saveDebugValues(String valueline) {
        /* Debugging:
        saveDebugStatus(dStr.toString());
        */
        if(mCheckSDCard.checkWriteSDCard()) {

            //Save data to file
            SimpleDateFormat sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
            Date actualdate = new Date(System.currentTimeMillis());
            String directoryname = Environment.getExternalStorageDirectory() + File.separator + mDIRECTORY;


            File directory = new File(directoryname);
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    return -1;
                }
            }


            String filename = sdformat.format(actualdate) + ".out";
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

    int saveStatistics(long timems, float stepscumul, float heightcumul, String type) {
        if(mCheckSDCard.checkWriteSDCard()) {
            SimpleDateFormat sdformat, sdformati;
            final String filenamet;
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
            boolean autoclean;
            int keep;
            //Save data to file
            switch (type) {
                case mSTAT_TYPE_DAILY:
                    sdformat = new SimpleDateFormat("yyyy", Locale.US);
                    sdformati = new SimpleDateFormat("yyyy-MM-dd, zzz", Locale.US); // fixed formatting, not local formatting, day is enough
                    filenamet = mFILENAME_STAT_DAILY;
                    autoclean = false;
                    keep = 9999;
                    break;
                case mSTAT_TYPE_REGULAR:
                    sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                    sdformati = new SimpleDateFormat("yyyy-MM-dd HH:mm, zzz", Locale.US); //seconds not needed
                    filenamet = mFILENAME_STAT_REG;
                    autoclean = settings.getBoolean(mPREF_STAT_HOUR_CLEAR, false);
                    keep = Integer.valueOf(
                            settings.getString(mPREF_STAT_HOUR_CLEAR_NUM, "9999"));
                    // to save the last interval not at next day 0:00, but on this day 24:00 we adjust the time a little bit
                    timems = timems - 1;
                    break;
                default:
                    sdformat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                    sdformati = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss, zzz", Locale.US); // fixed formatting, not local formatting
                    filenamet = mFILENAME_STAT_DETAIL;
                    autoclean = settings.getBoolean(mPREF_STAT_DETAIL_CLEAR, false);
                    keep = Integer.valueOf(
                            settings.getString(mPREF_STAT_DETAIL_CLEAR_NUM, "9999"));
            }

            // Always keep one file
            if (keep < 1) keep = 1;

            String directoryname = Environment.getExternalStorageDirectory() + File.separator + mDIRECTORY;


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
            outline = outline + String.format(Locale.US, "%.0f, %.0f, ", stepscumul, heightcumul) + type + "\n";

            try {
                String headline;
                if (!file.exists()) {
                    // Create a new file with header
                    out = new FileOutputStream(file);
                    headline = "Time,steps,elevation gain\n";
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
                                filelist[i].delete();
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