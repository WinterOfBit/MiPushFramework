package top.trumeet.mipushframework;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.xiaomi.xmsf.BuildConfig;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        XposedBridge.log("Loaded app: " + lpparam.packageName + " process:" + lpparam.processName);

        if ("android".equals(lpparam.packageName)) {
            hookSystemServer(lpparam.classLoader);
            return;
        }

        if (BuildConfig.APPLICATION_ID.equals(lpparam.packageName)) {
            hookSdk();
            return;
        }
    }

    private void hookSystemServer(ClassLoader classLoader) {

        Class<?> classPreferencesHelper = XposedHelpers.findClass("com.android.server.notification.PreferencesHelper", classLoader);
        XposedHelpers.findAndHookMethod(classPreferencesHelper, "isDelegateAllowed", String.class, int.class, String.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                String potentialDelegatePkg = (String) param.args[2];
                if (BuildConfig.APPLICATION_ID.equals(potentialDelegatePkg)) {
                    param.setResult(true);
                }
            }
        });

        Class<?> classNotificationManagerService = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService", classLoader);
        XposedHelpers.findAndHookMethod(classNotificationManagerService, "isUidSystemOrPhone", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                PackageManager pm = (PackageManager) XposedHelpers.getObjectField(param.thisObject, "mPackageManagerClient");
                int uid = (int) param.args[0];
                try {
                    ApplicationInfo info = pm.getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
                    if (info.uid == uid) {
                        param.setResult(true);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                }
            }
        });
    }

    private void hookSdk() {
    }

    public static void hookXM(Object o1) {
        Log.i("hookXM", "hooking: " + o1.toString());
    }

    public static void hookXM2(Object o1, Object o2) {

        Log.i("hookXM", "hooking: " + o1.getClass().getName() + "," + o2.toString());
    }
}
