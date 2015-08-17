/**
 *  Copyright (C) 2013  Dominik Schürmann <dominik@dominikschuermann.de>
 *  Copyright (C) 2010-2011  Lukas Aichbauer
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.ical;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.util.CompatibilityHints;

import org.sufficientlysecure.ical.ui.MainActivity;
import org.sufficientlysecure.ical.ui.SettingsActivity;
import org.sufficientlysecure.ical.ui.dialogs.Credentials;
import org.sufficientlysecure.ical.ui.dialogs.DialogTools;
import org.sufficientlysecure.ical.ui.dialogs.RunnableWithProgress;
import org.sufficientlysecure.ical.util.BasicInputAdapter;
import org.sufficientlysecure.ical.util.CredentialInputAdapter;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

@SuppressLint("NewApi")
public class Controller implements OnClickListener {
    private static final String TAG = Controller.class.getName();
    private static final String PREF_LAST_URL = "lastUrl";
    private static final String PREF_LAST_USERNAME = "lastUrlUsername";
    private static final String PREF_LAST_PASSWORD = SettingsActivity.PREF_LAST_URL_PASSWORD;

    private MainActivity activity;
    public static CalendarBuilder calendarBuilder;
    private Calendar calendar;

    public Controller(MainActivity activity) {
        this.activity = activity;
    }

    public void init(long calendarId) {
        List<AndroidCalendar> cals = AndroidCalendar.loadAll(activity.getContentResolver());
        if (cals.isEmpty()) {
            noCalendarFinish();
        }
        activity.setCalendars(cals);
        activity.selectCalendar(calendarId);
    }

    private void noCalendarFinish() {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setMessage(R.string.dialog_exiting)
                        .setIcon(R.drawable.icon)
                        .setTitle(R.string.dialog_information_title)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                        activity.finish();
                                    }
                                }).create();
                dialog.show();
            }
        });
    }

    private void setHint(SharedPreferences prefs, String key) {
        CompatibilityHints.setHintEnabled(key, prefs.getBoolean(key, true));
    }

    @Override
    public void onClick(View v) {
        // Handling search for file event
        if (v.getId() == R.id.SearchButton) {
            RunnableWithProgress run = new RunnableWithProgress(activity) {
                @Override
                public void run(ProgressDialog dialog) {
                    setProgressMessage(R.string.progress_searching_ical_files);

                    List<File> files = CalendarUtils.searchFiles(
                            Environment.getExternalStorageDirectory(), "ics", "ical", "icalendar");
                    List<BasicInputAdapter> urls = new ArrayList<BasicInputAdapter>(files.size());

                    for (File file : files) {
                        try {
                            urls.add(new BasicInputAdapter(file.toURL()));
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                    // Collections.sort(urls, new );
                    activity.setUrls(urls);
                }
            };
            DialogTools.runWithProgress(activity, run, false);
        }

        else if (v.getId() == R.id.LoadButton) {
            RunnableWithProgress run = new RunnableWithProgress(activity) {
                @Override
                public void run(ProgressDialog dialog) {
                    if (calendarBuilder == null) {
                        setProgressMessage(R.string.progress_loading_builder);
                        calendarBuilder = new CalendarBuilder();
                    }
                    try {
                        setProgressMessage(R.string.progress_reading_ical);
                        InputStream in = activity.getSelectedURL().getConnection().getInputStream();
                        if (in != null) {
                            SharedPreferences prefs = activity.preferences;
                            setHint(prefs, CompatibilityHints.KEY_RELAXED_UNFOLDING);
                            setHint(prefs, CompatibilityHints.KEY_RELAXED_PARSING);
                            setHint(prefs, CompatibilityHints.KEY_RELAXED_VALIDATION);
                            setHint(prefs, CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY);
                            setHint(prefs, CompatibilityHints.KEY_NOTES_COMPATIBILITY);
                            setHint(prefs, CompatibilityHints.KEY_VCARD_COMPATIBILITY);
                            calendar = calendarBuilder.build(in);
                        }
                        activity.setCalendar(calendar);
                    } catch (Exception exc) {
                        DialogTools.showInformationDialog(activity, R.string.dialog_error_title,
                                activity.getString(R.string.dialog_error_unparseable)
                                        + exc.getMessage(), R.drawable.icon);
                        Log.d(TAG, "Error", exc);
                    }
                }
            };
            DialogTools.runWithProgress(activity, run, false);
        } else if (v.getId() == R.id.SetUrlButton) {
            RunnableWithProgress run = new RunnableWithProgress(activity) {
                @Override
                public void run(ProgressDialog dialog) {
                    SharedPreferences prefs = activity.preferences;
                    String answer = DialogTools.questionDialog(activity,
                            R.string.dialog_enter_url_title, R.string.dialog_enter_url_message,
                            prefs.getString(PREF_LAST_URL, ""), true, false);
                    if (answer != null && !answer.equals("")) {
                        try {
                            String username = DialogTools.questionDialog(activity,
                                    R.string.dialog_enter_username_title,
                                    R.string.dialog_enter_username_message,
                                    prefs.getString(PREF_LAST_USERNAME, ""), true,
                                    false);
                            String password = null;
                            if (username != null && !username.equals("")) {
                                password = DialogTools.questionDialog(activity,
                                        R.string.dialog_enter_password_title,
                                        R.string.dialog_enter_password_message,
                                        prefs.getString(PREF_LAST_PASSWORD, ""), true,
                                        true);
                            }
                            setProgressMessage(R.string.progress_parsing_url);
                            URL url = new URL(answer);
                            Editor editor = prefs.edit();
                            editor.putString(PREF_LAST_URL, answer);
                            editor.putString(PREF_LAST_USERNAME, username);
                            boolean save = prefs.getBoolean("setting_save_passwords", false);
                            editor.putString(PREF_LAST_PASSWORD, save ? password : "");
                            editor.commit();
                            if (username != null && !username.equals("") && password != null) {
                                activity.setUrls(Arrays
                                        .asList((BasicInputAdapter) new CredentialInputAdapter(url,
                                                new Credentials(username, password))));
                            } else {
                                activity.setUrls(Arrays.asList(new BasicInputAdapter(url)));
                            }
                        } catch (MalformedURLException exc) {
                            Log.d(TAG, "Controller", exc);

                            DialogTools.showInformationDialog(activity, R.string.dialog_error_title,
                                    "URL was not parseable..." + exc.getMessage(), R.drawable.icon);
                        }
                    }
                }
            };
            DialogTools.runWithProgress(activity, run, false);
        } else if (v.getId() == R.id.SaveButton) {

            DialogTools.runWithProgress(activity,
                    new SaveCalendar(activity, activity.getSelectedCalendar()), false,
                    ProgressDialog.STYLE_HORIZONTAL);

        } else if (v.getId() == R.id.InsertButton || v.getId() == R.id.DeleteButton) {

            RunnableWithProgress dialog = new ProcessVEvent(activity, calendar,
                    activity.getSelectedCalendar(), v.getId() == R.id.InsertButton);
            DialogTools.runWithProgress(activity, dialog, false, ProgressDialog.STYLE_HORIZONTAL);
        }
    }
}
