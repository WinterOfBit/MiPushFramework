package com.xiaomi.push.sdk;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.catchingnow.icebox.sdk_client.IceBox;
import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.push.service.MIPushNotificationHelper;
import com.xiaomi.push.service.MyMIPushNotificationHelper;
import com.xiaomi.push.service.PushConstants;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import com.xiaomi.xmsf.push.notification.NotificationController;
import com.xiaomi.xmsf.utils.ConfigCenter;

import java.util.function.Consumer;

import top.trumeet.common.Constants;
import top.trumeet.common.db.RegisteredApplicationDb;
import top.trumeet.common.ita.ITopActivity;
import top.trumeet.common.ita.TopActivityFactory;
import top.trumeet.common.register.RegisteredApplication;
import top.trumeet.common.utils.Utils;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;

/**
 * @author zts1993
 * @date 2018/2/9
 */

public class MyPushMessageHandler extends IntentService {
    private static Logger logger = XLog.tag("MyPushMessageHandler").build();

    private static final int APP_CHECK_FRONT_MAX_RETRY = 8;
    private static final int APP_CHECK_SLEEP_DURATION_MS = 500;
    private static final int APP_CHECK_SLEEP_MAX_TIMEOUT_MS = APP_CHECK_FRONT_MAX_RETRY * APP_CHECK_SLEEP_DURATION_MS;

    static ITopActivity iTopActivity = null;

    public MyPushMessageHandler() {
        super("my mipush message handler");
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        byte[] payload = intent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD);
        if (payload == null) {
            logger.e("mipush_payload is null");
            return;
        }

        final XmPushActionContainer container = new XmPushActionContainer();
        try {
            XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload);
        } catch (Throwable var3) {
            logger.e(var3);
            return;
        }

        try {
            if (startService(this, container, payload) != null) {
                int notificationId = intent.getIntExtra(Constants.INTENT_NOTIFICATION_ID, 0);
                String notificationGroup = intent.getStringExtra(Constants.INTENT_NOTIFICATION_GROUP);
                boolean groupOfSession = intent.getBooleanExtra(Constants.INTENT_NOTIFICATION_GROUP_OF_SESSION, false);

                RegisteredApplication application = RegisteredApplicationDb.registerApplication(
                        container.getPackageName(), false, this, null);
                boolean isClearAllNotificationsOfSession = groupOfSession &&
                        application != null &&
                        application.isGroupNotificationsForSameSession() &&
                        application.isClearAllNotificationsOfSession();

                NotificationController.cancel(this, container,
                        notificationId, notificationGroup, isClearAllNotificationsOfSession);
            }
        } catch (Exception e) {
            logger.e(e.getLocalizedMessage(), e);
        }

    }

    public static void launchApp(Context context, XmPushActionContainer container) {
        if (iTopActivity == null) {
            iTopActivity = TopActivityFactory.newInstance(ConfigCenter.getInstance().getAccessMode(context));
        }

        if (!iTopActivity.isEnabled(context)) {
            iTopActivity.guideToEnable(context);
            return;
        }

        String targetPackage = container.getPackageName();

        activeApp(context, targetPackage);
        pullUpApp(context, targetPackage, container);
    }

    public static ComponentName startService(Context context, XmPushActionContainer container, byte[] payload) {
        launchApp(context, container);

        PushMetaInfo metaInfo = container.getMetaInfo();
        String targetPackage = container.getPackageName();

        final Intent localIntent = new Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE);
        localIntent.setComponent(new ComponentName(targetPackage, "com.xiaomi.mipush.sdk.PushMessageHandler"));
        localIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload);
        localIntent.putExtra(MIPushNotificationHelper.FROM_NOTIFICATION, true);
        localIntent.addCategory(String.valueOf(metaInfo.getNotifyId()));
        logger.d(packageInfo(targetPackage, "send to service"));
        ComponentName componentName = context.startService(localIntent);
        return componentName;
    }

    private static void activeApp(Context context, String targetPackage) {
        try {
            if (!ConfigCenter.getInstance().isIceboxSupported(context)) {
                return;
            }

            if (!Utils.isAppInstalled(IceBox.PACKAGE_NAME)) {
                return;
            }

            if (ContextCompat.checkSelfPermission(context, IceBox.SDK_PERMISSION) == PackageManager.PERMISSION_GRANTED) {

                int enabledSetting = IceBox.getAppEnabledSetting(context, targetPackage);
                if (enabledSetting != 0) {
                    logger.w(packageInfo(targetPackage, "active app by IceBox SDK"));
                    IceBox.setAppEnabledSettings(context, true, targetPackage);
                }

            } else {
                logger.w(packageInfo(targetPackage, "skip active app by IceBox SDK due to lack of permissions"));
            }
        } catch (Throwable e) {
            logger.e(packageInfo(targetPackage, "activeApp failed " + e.getLocalizedMessage()), e);
        }
    }


    private static Intent getJumpIntent(Context context, String targetPackage, XmPushActionContainer container) {
        Intent intent = MyMIPushNotificationHelper.getSdkIntent(context, targetPackage, container);
        if (intent == null) {
            intent = getJumpIntentFromPkg(context, targetPackage);
        }
        return intent;
    }

    private static Intent getJumpIntentFromPkg(Context context, String targetPackage) {
        Intent intent = null;
        try {
            intent = context.getPackageManager().getLaunchIntentForPackage(targetPackage);
        } catch (RuntimeException ignore) {
        }
        return intent;
    }

    private void runWithAppStateElevatedToForeground(final String pkg, final Consumer<Boolean> task) {
        if (SDK_INT < P) {      // onNullBinding() was introduced in Android P.
            task.accept(Boolean.FALSE);
            return;
        }
        final Intent intent = new Intent().setClassName(pkg, MyMIPushNotificationHelper.CLASS_NAME_PUSH_MESSAGE_HANDLER);
        final Context appContext = getApplicationContext();
        final boolean successful = appContext.bindService(intent, new ServiceConnection() {

            private void runTaskAndUnbind() {
                task.accept(Boolean.TRUE);
                appContext.unbindService(this);
            }

            @RequiresApi(P) @Override public void onNullBinding(final ComponentName name) {
                runTaskAndUnbind();
            }

            @Override public void onServiceConnected(final ComponentName name, final IBinder service) {
                runTaskAndUnbind();     // Should not happen
            }

            @Override public void onServiceDisconnected(final ComponentName name) {}
        }, BIND_AUTO_CREATE | BIND_IMPORTANT | BIND_ABOVE_CLIENT);

        if (! successful) task.accept(Boolean.FALSE);
    }

    private static long pullUpApp(Context context, String targetPackage, XmPushActionContainer container) {
        long start = System.currentTimeMillis();

        try {


            if (!iTopActivity.isAppForeground(context, targetPackage)) {
                logger.d(packageInfo(targetPackage, "app is not at front , let's pull up"));

                Intent intent = getJumpIntent(context, targetPackage, container);

                if (intent == null) {
                    throw new RuntimeException("can not get default activity for " + targetPackage);
                } else {
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    context.startActivity(intent);
                    logger.d(packageInfo(targetPackage, "start activity"));
                }


                //wait
                for (int i = 0; i < APP_CHECK_FRONT_MAX_RETRY; i++) {

                    if (!iTopActivity.isAppForeground(context, targetPackage)) {
                        Thread.sleep(APP_CHECK_SLEEP_DURATION_MS);
                    } else {
                        break;
                    }

                    if (i == (APP_CHECK_FRONT_MAX_RETRY / 2)) {
                        intent = getJumpIntentFromPkg(context, targetPackage);
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        context.startActivity(intent);
                    }
                }

                if ((System.currentTimeMillis() - start) >= APP_CHECK_SLEEP_MAX_TIMEOUT_MS) {
                    logger.w(packageInfo(targetPackage, "pull up app timeout"));
                }

            } else {
                logger.d(packageInfo(targetPackage, "app is at foreground"));
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            logger.e(packageInfo(targetPackage, "pullUpApp failed " + e.getLocalizedMessage()), e);
        }


        long end = System.currentTimeMillis();
        return end - start;

    }

    private static String packageInfo(String packageName, String message) {
        return "[" + packageName +"] " + message;
    }
}

