package top.trumeet.common.utils;

import static android.content.Context.APP_OPS_SERVICE;

import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Html;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.Calendar;
import java.util.Date;

import top.trumeet.common.override.AppOpsManagerOverride;

public final class Utils {
    public static int myUid() {
        return Process.myUserHandle().hashCode();
    }

    public static Application getApplication() {
        return AppGlobals.getInitialApplication();
    }

    public static PackageManager getPackageManager() {
        return AppGlobals.getInitialApplication().getPackageManager();
    }

    public static boolean isAppOpsInstalled() {
        return isAppInstalled("rikka.appops");
    }

    public static boolean isAppInstalled(String packageName) {
        try {
            return getPackageManager().getPackageInfo(packageName, 0) != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @ColorInt
    public static int getColorAttr(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        @ColorInt int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    public static Date getUTC(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int zoneOffset = cal.get(java.util.Calendar.ZONE_OFFSET);
        int dstOffset = cal.get(java.util.Calendar.DST_OFFSET);
        cal.add(java.util.Calendar.MILLISECOND, -(zoneOffset + dstOffset));
        return cal.getTime();
    }

    public static Date getUTC() {
        return getUTC(new Date());
    }

    public static CharSequence getString(@StringRes int id,
                                         @NonNull Context context,
                                         Object... formatArgs) {
        return toHtml(context.getString(id, formatArgs));
    }

    public static CharSequence toHtml(String str) {
        return Html.fromHtml(str);
    }

    public static boolean isUserApplication(ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
    }

    public static int checkOp(Context context, int op) {
        return AppOpsManagerOverride.checkOpNoThrow(op, Process.myUid(),
                context.getPackageName(), (AppOpsManager) context.getSystemService(APP_OPS_SERVICE));
    }

    public static boolean isUserApplication(String pkg) {
        try {
            return isUserApplication(getApplication().getPackageManager().getApplicationInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES));
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    public static void makeText(Context context, CharSequence usedString, int duration) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context, usedString, duration).show();
            } catch (Throwable ignored) {
                // TODO: It's a bad way to switch to main thread.
                // Ignored service death
            }
        });
    }

}
