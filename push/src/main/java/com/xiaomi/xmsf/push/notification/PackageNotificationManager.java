package com.xiaomi.xmsf.push.notification;

import android.app.AndroidAppHelper;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;
import top.trumeet.mipushframework.control.OnConnectStatusChangedListener;

// https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/notification/NotificationManagerService.java
class PackageNotificationManager implements INotificationManager {

    private static final String ANDROID_PACKAGE_NAME = "android";

    private final Context context;
    private final String packageName;
    private final Object manager = getService();

    private static Object getService() {
        return XposedHelpers.callStaticMethod(NotificationManager.class, "getService");
    }


    private int getUid(String packageName) {
        try {
            return AndroidAppHelper.currentApplication().getPackageManager().getApplicationInfo(packageName, 0).uid;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private int getUserId(Context context) {
        try {
            return (int) XposedHelpers.findMethodExact(context.getClass(), "getUserId")
                    .invoke(context);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public PackageNotificationManager(Context context, String packageName) {
        this.context = context;
        this.packageName = packageName;
    }

    @Override
    public void createNotificationChannelGroup(@NonNull NotificationChannelGroup group) {
        Object channelsList = null;
        Class<?> classParceledListSlice = null;
        try {
            classParceledListSlice = XposedHelpers.findClass("android.content.pm.ParceledListSlice", null);
            channelsList = XposedHelpers.findConstructorExact(classParceledListSlice, List.class)
                    .newInstance(Collections.singletonList(group));
            XposedHelpers.findMethodExact(manager.getClass(), "createNotificationChannelsForPackage", String.class, int.class, classParceledListSlice)
                    .invoke(manager, packageName, getUid(packageName), channelsList);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteNotificationChannelGroup(@NonNull String groupId) {
        try {
            XposedHelpers.findMethodExact(manager.getClass(), "deleteNotificationChannelGroup", String.class, String.class)
                    .invoke(manager, packageName, groupId);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public NotificationChannel getNotificationChannel(@NonNull String channelId) {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, String conversationId, boolean includeDeleted);
                    channel = (NotificationChannel) XposedHelpers.findMethodExact(manager.getClass(),
                                    "getNotificationChannelForPackage", String.class, int.class, String.class, String.class, Boolean.class)
                            .invoke(manager, packageName, getUid(packageName), channelId, null, false);

                } else {
                    //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, boolean includeDeleted);
                    channel = (NotificationChannel) XposedHelpers.findMethodExact(manager.getClass(),
                                    "getNotificationChannelForPackage", String.class, int.class, String.class, Boolean.class)
                            .invoke(manager, packageName, getUid(packageName), channelId, false);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return channel;
    }

    @Override
    public void createNotificationChannel(@NonNull NotificationChannel channel) {
        Object channelsList = null;
        Class<?> classParceledListSlice = null;
        try {
            classParceledListSlice = XposedHelpers.findClass("android.content.pm.ParceledListSlice", null);
            channelsList = XposedHelpers.findConstructorExact(classParceledListSlice, List.class)
                    .newInstance(Collections.singletonList(channel));
            XposedHelpers.findMethodExact(manager.getClass(), "createNotificationChannelsForPackage", String.class, int.class, classParceledListSlice)
                    .invoke(manager, packageName, getUid(packageName), channelsList);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteNotificationChannel(@NonNull String channelId) {
        try {
            XposedHelpers.findMethodExact(manager.getClass(), "deleteNotificationChannel", String.class, String.class)
                    .invoke(manager, packageName, channelId);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void notify(int id, @NonNull Notification notification) {
        //enqueueNotificationWithTag(String pkg, String opPkg, String tag, int id, Notification notification, int userId)
        Method methodEnqueueNotificationWithTag = XposedHelpers.findMethodExact(manager.getClass(), "enqueueNotificationWithTag", String.class, String.class, String.class, int.class, Notification.class, int.class);
        String opPkg = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ANDROID_PACKAGE_NAME : packageName;
        try {
            methodEnqueueNotificationWithTag.invoke(manager, packageName, opPkg, null, id, notification, getUserId(context));
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void cancel(int id) {

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
                Method methodCancelNotificationWithTag = XposedHelpers.findMethodExact(manager.getClass(), "cancelNotificationWithTag", String.class, String.class, String.class, int.class, int.class);
                methodCancelNotificationWithTag.invoke(manager, packageName, ANDROID_PACKAGE_NAME, null, id, getUserId(context));
            } else {
                //  public void cancelNotificationWithTag(String pkg, String tag, int id, int userId)
                Method methodCancelNotificationWithTag = XposedHelpers.findMethodExact(manager.getClass(), "cancelNotificationWithTag", String.class, String.class, int.class, int.class);
                methodCancelNotificationWithTag.invoke(manager, packageName, null, id, getUserId(context));
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public StatusBarNotification[] getActiveNotifications() {
        Method methodGetAppActiveNotifications = XposedHelpers.findMethodExact(manager.getClass(), "getAppActiveNotifications", String.class, int.class);
        try {
            Object parceledListSlice = methodGetAppActiveNotifications.invoke(manager, packageName, getUserId(context));
            Method methodGetList = XposedHelpers.findMethodExact(parceledListSlice.getClass(), "getList");
            List<StatusBarNotification> list = (List<StatusBarNotification>) methodGetList.invoke(parceledListSlice);
            return (StatusBarNotification[]) list.toArray();
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return new StatusBarNotification[0];
    }
}
