package hu.filc.naplo.widget_timetable;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import hu.filc.naplo.database.DBManager;
import hu.filc.naplo.MainActivity;
import hu.filc.naplo.R;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

import es.antonborri.home_widget.HomeWidgetBackgroundIntent;
import es.antonborri.home_widget.HomeWidgetLaunchIntent;
import es.antonborri.home_widget.HomeWidgetProvider;

public class WidgetTimetable extends HomeWidgetProvider {

    private static final String ACTION_WIDGET_CLICK_NAV_LEFT = "list_widget.ACTION_WIDGET_CLICK_NAV_LEFT";
    private static final String ACTION_WIDGET_CLICK_NAV_RIGHT = "list_widget.ACTION_WIDGET_CLICK_NAV_RIGHT";
    private static final String ACTION_WIDGET_CLICK_NAV_TODAY = "list_widget.ACTION_WIDGET_CLICK_NAV_TODAY";
    private static final String ACTION_WIDGET_CLICK_NAV_REFRESH = "list_widget.ACTION_WIDGET_CLICK_NAV_REFRESH";
    private static final String ACTION_WIDGET_CLICK_BUY_PREMIUM = "list_widget.ACTION_WIDGET_CLICK_BUY_PREMIUM";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds, SharedPreferences widgetData) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            RemoteViews views = generateView(context, appWidgetIds[i]);

            if(premiumEnabled(context) && userLoggedIn(context)) {
                int rday = selectDay(context, appWidgetIds[i], 0, true);
                views.setTextViewText(R.id.nav_current, convertDayOfWeek(context, rday));
            }

            pushUpdate(context, views, appWidgetIds[i]);
        }
    }

    public static void pushUpdate(Context context, RemoteViews remoteViews, int appWidgetSingleId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);

        manager.updateAppWidget(appWidgetSingleId, remoteViews);
        manager.notifyAppWidgetViewDataChanged(appWidgetSingleId, R.id.widget_list);
    }

    public static RemoteViews generateView(Context context, int appId) {
        Intent serviceIntent = new Intent(context, WidgetTimetableService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_timetable);

        views.setViewVisibility(R.id.need_premium, View.GONE);
        views.setViewVisibility(R.id.need_login, View.GONE);
        views.setViewVisibility(R.id.tt_grid_cont, View.GONE);

        if(!userLoggedIn(context)) {
            views.setViewVisibility(R.id.need_login, View.VISIBLE);
            views.setOnClickPendingIntent(R.id.open_login, makePending(context, ACTION_WIDGET_CLICK_BUY_PREMIUM, appId));
        } else if(premiumEnabled(context)) {
            views.setViewVisibility(R.id.tt_grid_cont, View.VISIBLE);
            views.setOnClickPendingIntent(R.id.nav_to_left, makePending(context, ACTION_WIDGET_CLICK_NAV_LEFT, appId));
            views.setOnClickPendingIntent(R.id.nav_to_right, makePending(context, ACTION_WIDGET_CLICK_NAV_RIGHT, appId));
            views.setOnClickPendingIntent(R.id.nav_current, makePending(context, ACTION_WIDGET_CLICK_NAV_TODAY, appId));
            views.setOnClickPendingIntent(R.id.nav_refresh, makePending(context, ACTION_WIDGET_CLICK_NAV_REFRESH, appId));
            views.setRemoteAdapter(R.id.widget_list, serviceIntent);
            views.setEmptyView(R.id.widget_list, R.id.empty_view);
        } else  {
            views.setViewVisibility(R.id.need_premium, View.VISIBLE);
            views.setOnClickPendingIntent(R.id.buy_premium, makePending(context, ACTION_WIDGET_CLICK_BUY_PREMIUM, appId));
        }

        return views;
    }

    static PendingIntent makePending(Context context, String action, int appWidgetId) {
        Intent activebtnnext = new Intent(context, WidgetTimetable.class);
        activebtnnext.setAction(action);
        activebtnnext.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getBroadcast(context, appWidgetId, activebtnnext , PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if(intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID)) {
            int appId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            RemoteViews views = generateView(context, appId);

            try {
                if(premiumEnabled(context) && userLoggedIn(context)) {
                    if (intent.getAction().equals(ACTION_WIDGET_CLICK_NAV_LEFT)) {
                        int rday = selectDay(context, appId, -1, false);
                        views.setTextViewText(R.id.nav_current, convertDayOfWeek(context, rday));

                        pushUpdate(context, views, appId);
                    } else if (intent.getAction().equals(ACTION_WIDGET_CLICK_NAV_RIGHT)) {
                        int rday = selectDay(context, appId, 1, false);
                        views.setTextViewText(R.id.nav_current, convertDayOfWeek(context, rday));

                        pushUpdate(context, views, appId);
                    } else if (intent.getAction().equals(ACTION_WIDGET_CLICK_NAV_TODAY)) {
                        int rday = getToday(context);
                        setSelectedDay(context, appId, rday);

                        views.setTextViewText(R.id.nav_current, convertDayOfWeek(context, rday));

                        pushUpdate(context, views, appId);
                    } else if (intent.getAction().equals(ACTION_WIDGET_CLICK_NAV_REFRESH)) {
                        PendingIntent pendingIntent = HomeWidgetLaunchIntent.INSTANCE.getActivity(context, MainActivity.class, Uri.parse("timetable://refresh"));
                        pendingIntent.send();
                    } else if (intent.getAction().equals("android.appwidget.action.APPWIDGET_DELETED")) {
                        DBManager dbManager = new DBManager(context.getApplicationContext());

                        try {
                            dbManager.open();
                            dbManager.deleteWidget(appId);
                            dbManager.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                if(intent.getAction().equals(ACTION_WIDGET_CLICK_BUY_PREMIUM)) {
                    PendingIntent pendingIntent = HomeWidgetLaunchIntent.INSTANCE.getActivity(context, MainActivity.class, Uri.parse("settings://premium"));
                    pendingIntent.send();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String convertDayOfWeek(Context context, int rday) {

        /*if(rday == -1) return DayOfWeek.of(1).getDisplayName(TextStyle.FULL, new Locale("hu", "HU"));

        String dayOfWeek = DayOfWeek.of(rday + 1).getDisplayName(TextStyle.FULL, new Locale("hu", "HU"));*/

        String dayOfWeek = "Unknown";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Locale loc = getLocale(context);

            if (rday == -1)
                return DayOfWeek.of(1).getDisplayName(TextStyle.FULL, loc);

            dayOfWeek = DayOfWeek.of(rday + 1).getDisplayName(TextStyle.FULL, loc);
        }

        return dayOfWeek.substring(0, 1).toUpperCase() + dayOfWeek.substring(1).toLowerCase();
    }

    public static void setSelectedDay(Context context, int wid, int day) {
        DBManager dbManager = new DBManager(context.getApplicationContext());

        try {
            dbManager.open();
            dbManager.update(wid, day);
            dbManager.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getToday(Context context) {
        int rday = new DateTime().getDayOfWeek() - 1;
        List<JSONArray> s = genJsonDays(context);

        try {
            if(checkIsAfter(s, rday)) rday += 1;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return retDay(rday, s.size());
    }

    public static int selectDay(Context context, int wid, int add, Boolean afterSubjects) {
        DBManager dbManager = new DBManager(context.getApplicationContext());

        try {
            dbManager.open();
            Cursor cursor = dbManager.fetchWidget(wid);

            List<JSONArray> s = genJsonDays(context);
            int retday = new DateTime().getDayOfWeek() - 1;

            if(cursor.getCount() != 0) retday = retDay(cursor.getInt(1) + add, s.size());

            if(afterSubjects) if(checkIsAfter(s, retday)) retday += 1;
            retday = retDay(retday, s.size());

            if(cursor.getCount() == 0) dbManager.insertSelDay(wid, retday);
            else dbManager.update(wid, retday);

            dbManager.close();

            return retday;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public static Boolean checkIsAfter(List<JSONArray> s, int retday) throws Exception {
        retday = retDay(retday, s.size());

        String vegIdopont = s.get(retday).getJSONObject(s.get(retday).length() - 1).getString("VegIdopont");

        return new DateTime().isAfter(new DateTime(vegIdopont));
    }

    public static int retDay(int retday, int size) {
        if (retday < 0) retday = size - 1;
        else if (retday > size - 1) retday = 0;

        return retday;
    }

    public static List<JSONArray> genJsonDays(Context context) {
        List<JSONArray> gen_days = new ArrayList<>();

        DBManager dbManager = new DBManager(context.getApplicationContext());
        try {
            dbManager.open();
            Cursor ct = dbManager.fetchTimetable();
            dbManager.close();

            if(ct.getCount() == 0) {
                return gen_days;
            }

            JSONArray fecthtt = new JSONArray(ct.getString(0));

            JSONArray dayArray = new JSONArray();
            String currday = "";

            for (int i=0; i < fecthtt.length(); i++)
            {
                try {
                    if(i == 0) currday = fecthtt.getJSONObject(0).getString("Datum");
                    JSONObject oraObj = fecthtt.getJSONObject(i);

                    if(!currday.equals(oraObj.getString("Datum"))) {
                        gen_days.add(dayArray);
                        currday = fecthtt.getJSONObject(i).getString("Datum");
                        dayArray = new JSONArray();
                    }

                    dayArray.put(oraObj);
                    if(i == fecthtt.length() - 1) {
                        gen_days.add(dayArray);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(gen_days, new Comparator<JSONArray>() {

            public int compare(JSONArray a, JSONArray b) {
                long valA = 0;
                long valB = 0;

                try {
                    new DateTime( a.getJSONObject(0).getString("Datum")).getMillis();
                    valA = (long) new DateTime( a.getJSONObject(0).getString("Datum")).getMillis();
                    valB = (long) new DateTime( b.getJSONObject(0).getString("Datum")).getMillis();
                }
                catch (JSONException ignored) {
                }

                return (int) (valA - valB);
            }
        });

        return gen_days;
    }

    public static String zeroPad(int value, int padding){
        StringBuilder b = new StringBuilder();
        b.append(value);
        while(b.length() < padding){
            b.insert(0,"0");
        }
        return b.toString();
    }

    public static Locale getLocale(Context context) {
        DBManager dbManager = new DBManager(context.getApplicationContext());

        try {
            dbManager.open();
            String loc = dbManager.fetchLocale().getString(0);
            dbManager.close();

            if(loc.equals("hu") || loc.equals("de")) {
                return new Locale(loc, loc.toUpperCase());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new Locale("en", "GB");
    }

    public static boolean premiumEnabled(Context context) {
        DBManager dbManager = new DBManager(context.getApplicationContext());

        try {
            dbManager.open();
            String premium_token = dbManager.fetchPremiumToken().getString(0);
            dbManager.close();

            if(!premium_token.equals("")) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean userLoggedIn(Context context) {
        return !lastUserId(context).equals("");
    }

    public static String lastUserId(Context context) {
        DBManager dbManager = new DBManager(context.getApplicationContext());
        try {
            dbManager.open();
            Cursor cursor = dbManager.fetchLastUser();
            dbManager.close();

            if(cursor != null && !cursor.getString(0).equals("")) {
                String last_user = cursor.getString(0);
                return last_user;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    @Override
    public void onEnabled(Context context) {
    }

    @Override
    public void onDisabled(Context context) {
    }
}