package com.xiaomi.xmsf;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.isAppMainProc;
import static com.xiaomi.xmsf.push.control.PushControllerUtils.wrapContext;
import static com.xiaomi.xmsf.push.notification.NotificationController.CHANNEL_WARN;
import static top.trumeet.common.Constants.TAG_CONDOM;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationChannelGroupCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.elvishew.xlog.XLog;
import com.oasisfeng.condom.CondomOptions;
import com.oasisfeng.condom.CondomProcess;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.LoggerInterface;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager;
import com.xiaomi.mipush.sdk.Logger;
import com.xiaomi.xmsf.push.control.PushControllerUtils;
import com.xiaomi.xmsf.push.control.XMOutbound;
import com.xiaomi.xmsf.push.notification.NotificationController;
import com.xiaomi.xmsf.push.service.MiuiPushActivateService;
import com.xiaomi.xmsf.utils.LogUtils;

import java.util.Objects;

import rx_activity_result2.RxActivityResult;
import top.trumeet.common.Constants;
import top.trumeet.common.push.PushServiceAccessibility;
import top.trumeet.mipush.provider.DatabaseUtils;

public class MiPushFrameworkApp extends Application {
    private com.elvishew.xlog.Logger logger;

    private static final String MIPUSH_EXTRA = "mipush_extra";

    private static final int[] RetryInterval = {3600000, 7200000, 14400000, 28800000, 86400000};

    public static void registerPush(MiPushFrameworkApp app, int i) {
        Objects.requireNonNull(app);
        int[] retryInterval = RetryInterval;
        int length = retryInterval.length;
        long intervalMs = i < length ? retryInterval[i] : retryInterval[length - 1];
        MyLog.i("for make sure xmsf register push succ, schedule register after " + intervalMs / 1000 + " sec");
        new Handler().postDelayed(new RetryRegister(app, i), intervalMs);
    }


    @Override
    public void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        DatabaseUtils.init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        RxActivityResult.register(this);

        LogUtils.init(this);
        logger = XLog.tag(MiPushFrameworkApp.class.getSimpleName()).build();
        logger.i("App starts at " + System.currentTimeMillis());

        initMiSdkLogger();
        initPushLogger();

        CondomOptions options = XMOutbound.create(this, TAG_CONDOM + "_PROCESS",
                false);
        CondomProcess.installExceptDefaultProcess(this, options);

        if (isAppMainProc(this)) {
            ScheduledJobManager.getInstance(wrapContext(this))
                    .addOneShootJob(new FirstRegister(this, wrapContext(this)));
        }
        PushControllerUtils.setAllEnable(true, this);

        long currentTimeMillis = System.currentTimeMillis();
        long lastStartupTime = getLastStartupTime();
        if (isAppMainProc(this)) {
            if ((currentTimeMillis - lastStartupTime > 300000 || currentTimeMillis - lastStartupTime < 0)) {
                setStartupTime(currentTimeMillis);
                MiuiPushActivateService.awakePushActivateService(wrapContext(this)
                        , "com.xiaomi.xmsf.push.SCAN");
            }
        }


        NotificationController.deleteOldNotificationChannelGroup(this);

        try {
            if (!PushServiceAccessibility.isInDozeWhiteList(this)) {
                NotificationManagerCompat manager = NotificationManagerCompat.from(this);
                notifyDozeWhiteListRequest(manager);
            }
        } catch (RuntimeException e) {
            logger.e(e.getMessage(), e);
        }


    }

    private void notifyDozeWhiteListRequest(NotificationManagerCompat manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelCompat.Builder channel = new NotificationChannelCompat
                    .Builder(CHANNEL_WARN, NotificationManager.IMPORTANCE_HIGH)
                    .setName(getString(R.string.wizard_title_doze_whitelist));

            NotificationChannelGroupCompat notificationChannelGroup =
                    new NotificationChannelGroupCompat.Builder(CHANNEL_WARN).setName(CHANNEL_WARN).build();
            manager.createNotificationChannelGroup(notificationChannelGroup);
            channel.setGroup(notificationChannelGroup.getId());
            manager.createNotificationChannel(channel.build());
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent()
                .setComponent(new ComponentName(Constants.SERVICE_APP_NAME,
                        Constants.REMOVE_DOZE_COMPONENT_NAME)), PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(this,
                CHANNEL_WARN)
                .setContentInfo(getString(R.string.wizard_title_doze_whitelist))
                .setContentTitle(getString(R.string.wizard_title_doze_whitelist))
                .setContentText(getString(R.string.wizard_descr_doze_whitelist))
                .setTicker(getString(R.string.wizard_descr_doze_whitelist))
                .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .setShowWhen(true)
                .setAutoCancel(true)
                .build();
        manager.notify(getClass().getSimpleName(), 100, notification);  // Use tag to avoid conflict with push notifications.
    }

    /**
     * The only purpose is to make sure Logger is created after the XLog is configured.
     */
    private LoggerInterface buildMiSDKLogger() {
        return new LoggerInterface() {
            private static final String TAG = "PushCore";
            private com.elvishew.xlog.Logger logger = XLog.tag(TAG).build();

            @Override
            public void setTag(String tag) {
                logger = XLog.tag(TAG + "-" + tag).build();
            }

            @Override
            public void log(String content, Throwable t) {
                if (t == null) {
                    logger.d(content);
                } else {
                    logger.d(content, t);
                }
            }

            @Override
            public void log(String content) {
                logger.d(content);
            }
        };
    }

    private void initPushLogger() {
        Logger.setLogger(wrapContext(this), buildMiSDKLogger());
    }

    private void initMiSdkLogger() {
        MyLog.setLogger(buildMiSDKLogger());
        if (SystemUtils.isDebuggable(this)) {
            MyLog.setLogLevel(MyLog.INFO);
        }
    }


    private long getLastStartupTime() {
        return getDefaultPreferences().getLong("xmsf_startup", 0);
    }

    private boolean setStartupTime(long j) {
        return getDefaultPreferences().edit().putLong("xmsf_startup", j).commit();
    }

    private SharedPreferences getDefaultPreferences() {
        return getSharedPreferences(MIPUSH_EXTRA, 0);
    }

}
