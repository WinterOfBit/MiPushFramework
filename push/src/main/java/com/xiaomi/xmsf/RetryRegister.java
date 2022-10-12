package com.xiaomi.xmsf;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.pushRegistered;

import static top.trumeet.common.Constants.APP_ID;
import static top.trumeet.common.Constants.APP_KEY;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.MiPushClient;

/* renamed from: com.xiaomi.xmsf.b */
/* loaded from: classes.dex */
public class RetryRegister implements Runnable {

    /* renamed from: a */
    final /* synthetic */ int tryRegisterCount;

    /* renamed from: b */
    final /* synthetic */ MiPushFrameworkApp app;

    public RetryRegister(MiPushFrameworkApp MiPushFrameworkApp, int i) {
        this.app = MiPushFrameworkApp;
        this.tryRegisterCount = i;
    }

    @Override // java.lang.Runnable
    public void run() {
        if (pushRegistered(this.app)) {
            MyLog.i("register successed, stop retry");
            return;
        }
        MiPushClient.registerPush(this.app, APP_ID, APP_KEY);
        int tryRegisterCount = this.tryRegisterCount + 1;
        if (tryRegisterCount <= 10) {
            MyLog.i("register not successed, register again, retryIndex: " + tryRegisterCount);
            MiPushFrameworkApp.registerPush(this.app, tryRegisterCount);
            return;
        }
        MyLog.i("register not successed, but retry to many times, stop retry");
    }
}
