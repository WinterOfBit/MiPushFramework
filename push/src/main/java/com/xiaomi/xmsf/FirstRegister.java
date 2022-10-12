package com.xiaomi.xmsf;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered;

import static top.trumeet.common.Constants.APP_ID;
import static top.trumeet.common.Constants.APP_KEY;

import android.content.Context;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.MiPushClient;

import java.util.Objects;

/* renamed from: com.xiaomi.xmsf.c */
/* loaded from: classes.dex */
class FirstRegister implements Runnable {

    /* renamed from: a */
    final /* synthetic */ Context context;

    /* renamed from: b */
    final /* synthetic */ MiPushFrameworkApp app;

    /* JADX INFO: Access modifiers changed from: package-private */
    public FirstRegister(MiPushFrameworkApp xmsfApp, Context context) {
        this.app = xmsfApp;
        this.context = context;
    }

    @Override // java.lang.Runnable
    public void run() {
        Objects.requireNonNull(this.app);
        MiPushClient.registerPush(this.context, APP_ID, APP_KEY);
        if (pushRegistered(this.context)) {
            MyLog.i("register successed");
        } else {
            MiPushFrameworkApp.registerPush(this.app, 0);
        }
        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            MyLog.e("register push interrupted error", e);
        }
    }
}
