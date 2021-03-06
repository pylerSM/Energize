package com.halcyonwaves.apps.energize.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import com.halcyonwaves.apps.energize.Consts;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * This class parses a XML changelog file and uses CSS to convert it to a nice looking
 * changelog which can be presented to the user after an application upgrade.
 * <p/>
 * The original version of this class was written by Martin van Zuilekom and was modified
 * to fulfill the my personal needs.
 *
 * @author Martin van Zuilekom
 * @author Tim Huetz
 */
public class ChangeLogDialog {

    static final private String TAG = "ChangeLogDialog";

    private Activity rootActivity = null;
    private SimpleDateFormat changelogDateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public ChangeLogDialog(final Activity context) {
        this.rootActivity = context;
    }

    private String getApplicationVersion() {
        try {
            final PackageInfo packageInfo = this.rootActivity.getPackageManager().getPackageInfo(this.rootActivity.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (final NameNotFoundException e) {
            return "";
        }
    }

    private int getApplicationVersionCode() {
        try {
            final PackageInfo packageInfo = this.rootActivity.getPackageManager().getPackageInfo(this.rootActivity.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (final NameNotFoundException e) {
            return -1;
        }
    }

    private String getChangelogStyleDefinition() {
        return "<style type=\"text/css\">" + "h1 { margin-left: 0px; margin-bottom: 0px; font-size: 12pt; }" + "h2 { margin-left: 0px; margin-top: 0px; font-size: 8pt; color: #7A7A7A; font-weight: normal; }" + "li { margin-left: 0px; font-size: 9pt;}" + "ul { padding-left: 30px;}" + "</style>";
    }

    private String getHtmlChangelog() {
        String _Result = "<html><head>" + this.getChangelogStyleDefinition() + "</head><body>";
        InputStream rawChangelog = null;

        try {
            //
            final XmlPullParserFactory xmlPullFactory = XmlPullParserFactory.newInstance();
            xmlPullFactory.setValidating(false);
            final XmlPullParser _xml = xmlPullFactory.newPullParser();
            rawChangelog = this.rootActivity.getApplicationContext().getAssets().open("xml/changelog.xml");
            _xml.setInput(rawChangelog, null);

            //
            int eventType = _xml.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if ((eventType == XmlPullParser.START_TAG) && (_xml.getName().equals("release"))) {
                    _Result = _Result + this.parseChangelogReleaseTag(_xml);

                }
                eventType = _xml.next();
            }
        } catch (final XmlPullParserException e) {
            Log.e(ChangeLogDialog.TAG, e.getMessage(), e);
        } catch (final IOException e) {
            Log.e(ChangeLogDialog.TAG, e.getMessage(), e);

        } finally {
            try {
                rawChangelog.close();
            } catch (final IOException e) {
                // TODO: handle this
            }
        }
        _Result = _Result + "</body></html>";
        return _Result;
    }

    private static Calendar getDatePart(Date date) {
        Calendar cal = Calendar.getInstance();       // get calendar instance
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);            // set hour to midnight
        cal.set(Calendar.MINUTE, 0);                 // set minute in hour
        cal.set(Calendar.SECOND, 0);                 // set second in minute
        cal.set(Calendar.MILLISECOND, 0);            // set millisecond in second

        return cal;                                  // return the date part
    }

    private static int daysBetween(Date startDate, Date endDate) {
        Calendar sDate = getDatePart(startDate);
        Calendar eDate = getDatePart(endDate);

        int daysBetween = 0;
        while (sDate.before(eDate)) {
            sDate.add(Calendar.DAY_OF_MONTH, 1);
            daysBetween++;
        }
        return daysBetween;
    }

    public void markDialogAsAlreadyDisplayed() {
        final SharedPreferences appPreferences = PreferenceManager.getDefaultSharedPreferences(this.rootActivity.getApplicationContext());

        //
        Editor prefEditor = appPreferences.edit();
        prefEditor.putInt(Consts.PREFERENCE_CHANGELOG_DISPLAYED_LAST_TIME, this.getApplicationVersionCode());
        prefEditor.commit();
        prefEditor = null;
    }

    private String parseChangelogReleaseTag(final XmlPullParser aXml) throws XmlPullParserException, IOException {
        // try to calculate the days since the release of the version
        int daysSinceRelease = -1;
        try {
            daysSinceRelease = ChangeLogDialog.daysBetween(this.changelogDateFormat.parse(aXml.getAttributeValue(null, "releasedate")), Calendar.getInstance().getTime());

        } catch (final ParseException e) {
            // failed to parse the date
        }

        String _Result = "<h1>Version " + aXml.getAttributeValue(null, "version") + "</h1><h2>Released on " + aXml.getAttributeValue(null, "releasedate") + " (" + daysSinceRelease + " days ago)</h2><ul>";
        int eventType = aXml.getEventType();
        while ((eventType != XmlPullParser.END_TAG) || (aXml.getName().equals("change"))) {
            if ((eventType == XmlPullParser.START_TAG) && (aXml.getName().equals("change"))) {
                eventType = aXml.next();
                _Result = _Result + "<li>" + aXml.getText() + "</li>";
            }
            eventType = aXml.next();
        }
        _Result = _Result + "</ul>";
        return _Result;
    }

    public void show() {
        final SharedPreferences appPreferences = PreferenceManager.getDefaultSharedPreferences(this.rootActivity.getApplicationContext());
        final int lastTimeDisplayed = appPreferences.getInt(Consts.PREFERENCE_CHANGELOG_DISPLAYED_LAST_TIME, -1);

        if (lastTimeDisplayed < this.getApplicationVersionCode()) {
            this.markDialogAsAlreadyDisplayed();

            //
            final String changelogDialogTitle = "Changelog " + " v" + this.getApplicationVersion(); // TODO: resources
            final String convertedHtmlChangelog = this.getHtmlChangelog();

            // Get button strings
            final String _Close = this.rootActivity.getString(android.R.string.ok);

            // Could not load change log, message user and exit void
            if (convertedHtmlChangelog.equals("")) {
                Toast.makeText(this.rootActivity, "Could not load change log", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create webview and load html
            final WebView _WebView = new WebView(this.rootActivity);
            _WebView.loadData(convertedHtmlChangelog, "text/html", "utf-8");
            final AlertDialog.Builder builder = new AlertDialog.Builder(this.rootActivity).setTitle(changelogDialogTitle).setView(_WebView).setPositiveButton(_Close, new Dialog.OnClickListener() {

                public void onClick(final DialogInterface dialogInterface, final int i) {
                    dialogInterface.dismiss();
                }
            });
            builder.create().show();
        }
    }

}
