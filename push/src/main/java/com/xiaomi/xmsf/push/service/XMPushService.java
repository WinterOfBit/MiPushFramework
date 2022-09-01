package com.xiaomi.xmsf.push.service;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.push.service.PushServiceMain;
import com.xiaomi.xmsf.R;
import com.xiaomi.xmsf.push.utils.RemoveTremblingUtils;
import com.xiaomi.xmsf.utils.ConfigCenter;

import top.trumeet.common.Constants;
import top.trumeet.common.cache.ApplicationNameCache;
import top.trumeet.common.db.EventDb;
import top.trumeet.common.db.RegisteredApplicationDb;
import top.trumeet.common.event.Event;
import top.trumeet.common.register.RegisteredApplication;

public class XMPushService extends IntentService {
    private static final String TAG = "XMPushService Bridge";
    private final Logger logger = XLog.tag(TAG).build();

    public XMPushService() {
        super("XMPushService Bridge");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            logger.d("onHandleIntent -> A application want to register push");
            String pkg = intent.getStringExtra(Constants.EXTRA_MI_PUSH_PACKAGE);
            if (pkg == null) {
                logger.e("Package name is NULL!");
                return;
            }
            // Check multi request
            boolean register = RemoveTremblingUtils.getInstance().onCallRegister(pkg);
            if (!register) {
                logger.d("Don't register multi request " + pkg);
            }
            RegisteredApplication application = RegisteredApplicationDb
                    .registerApplication(pkg, true, this, null);

            if (application == null) {
                return;
            }

            forwardToPushServiceMain(intent);

            if (register) {
                showRegisterToastIfExistsConfiguration(application);
                EventDb.insertEvent(Event.ResultType.OK,
                        new top.trumeet.common.event.type.RegistrationType(null, pkg, null),
                        this);
            }
        } catch (RuntimeException e) {
            logger.e("XMPushService::onHandleIntent: ", e);
            Toast.makeText(this, getString(R.string.common_err, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void showRegisterToastIfExistsConfiguration(RegisteredApplication application) {
        String pkg = application.getPackageName();
        boolean notificationOnRegister = ConfigCenter.getInstance().isNotificationOnRegister(this);
        notificationOnRegister = notificationOnRegister && application.isNotificationOnRegister();
        if (notificationOnRegister) {
            CharSequence appName = ApplicationNameCache.getInstance().getAppName(this, pkg);
            CharSequence usedString = getString(R.string.notification_registerAllowed, appName);
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Toast.makeText(this, usedString,
                            Toast.LENGTH_SHORT)
                            .show();
                } catch (Throwable ignored) {
                    // TODO: It's a bad way to switch to main thread.
                    // Ignored service death
                }
            });
        } else {
            Log.e("XMPushService Bridge", "Notification disabled");
        }
    }

    private void forwardToPushServiceMain(Intent intent) {
        Intent intent2 = new Intent();
        intent2.setComponent(new ComponentName(this, PushServiceMain.class));
        intent2.setAction(intent.getAction());
        intent2.putExtras(intent);
        ContextCompat.startForegroundService(this, intent2);
    }

}
