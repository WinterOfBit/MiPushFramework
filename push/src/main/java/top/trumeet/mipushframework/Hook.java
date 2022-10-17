package top.trumeet.mipushframework;

import static de.robv.android.xposed.XposedHelpers.findMethodExact;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.xiaomi.channel.commonutils.reflect.JavaCalls;
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

        Class<?> classNotificationManagerService = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService", classLoader)
        XposedHelpers.findAndHookMethod(classNotificationManagerService, "onBootPhase", int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                //com.android.server.SystemService#PHASE_BOOT_COMPLETED
                if (param.args[0] == 1000) {
                    Context context = JavaCalls.callMethod(param.thisObject, "getContext");
                    val stubClass = thisObject.get<Any>("mService").javaClass
                    hookPermission(stubClass)
                    hookSystemReadyFlag(stubClass)
                }

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

    private fun hookSystemReadyFlag(stubClass: Class<Any>) {
        stubClass.hookMethod("isSystemConditionProviderEnabled", String::class.java) {
            doBefore {
                if (args[0] == IS_SYSTEM_HOOK_READY) {
                    result = true
                }
            }
        }
    }


    private fun fromHms() = try {
        Binder.getCallingUid() == AndroidAppHelper.currentApplication().packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0).uid
    } catch (e: Throwable) {
        false
    }

    private fun tryHookPermission(packageName: String): Boolean {
        if (packageName != BuildConfig.APPLICATION_ID && fromHms()) {
            Binder.clearCallingIdentity()
            return true
        }
        return false
    }

    private fun hookPermission(targetPackageNameParamIndex: Int, hookExtra: (XC_MethodHook.MethodHookParam.() -> Unit)? = null): HookCallback = {
        doBefore {
            if (tryHookPermission(args[targetPackageNameParamIndex] as String)) {
                hookExtra?.invoke(this)
            }
        }
    }

    fun hookPermission(classINotificationManager: Class<*>) {
        //boolean areNotificationsEnabledForPackage(String pkg, int uid);
        findMethodExact(classINotificationManager, "areNotificationsEnabledForPackage", String::class.java, Int::class.java)
            .hook(hookPermission(0))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, String conversationId, boolean includeDeleted);
            findMethodExact(classINotificationManager, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, String::class.java, Boolean::class.java)
                .hook(hookPermission(0))
        } else {
            //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, boolean includeDeleted);
            findMethodExact(classINotificationManager, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, Boolean::class.java)
                .hook(hookPermission(0))
        }

        //void enqueueNotificationWithTag(String pkg, String opPkg, String tag, int id, Notification notification, int userId)
        findMethodExact(classINotificationManager, "enqueueNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Notification::class.java, Int::class.java)
            .hook(hookPermission(0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                args[1] = ANDROID_PACKAGE_NAME
            }
        })

        //void createNotificationChannelsForPackage(String pkg, int uid, in ParceledListSlice channelsList);
        findMethodExact(classINotificationManager, "createNotificationChannelsForPackage", String::class.java, Int::class.java, findClass("android.content.pm.ParceledListSlice", null))
            .hook(hookPermission(0))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
            findMethodExact(classINotificationManager, "cancelNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java)
                .hook(hookPermission(0) {
                args[2] = ANDROID_PACKAGE_NAME
            })
        } else {
            //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
            findMethodExact(classINotificationManager, "cancelNotificationWithTag", String::class.java, String::class.java, Int::class.java, Int::class.java)
                .hook(hookPermission(0))
        }

        //void deleteNotificationChannel(String pkg, String channelId);
        findMethodExact(classINotificationManager, "deleteNotificationChannel", String::class.java, String::class.java)
            .hook(hookPermission(0))

        //ParceledListSlice getAppActiveNotifications(String callingPkg, int userId);
        findMethodExact(classINotificationManager, "getAppActiveNotifications", String::class.java, Int::class.java)
            .hook(hookPermission(0))
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
