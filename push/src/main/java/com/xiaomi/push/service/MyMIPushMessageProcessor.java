package com.xiaomi.push.service;

import static com.xiaomi.push.service.MIPushEventProcessor.buildContainer;
import static com.xiaomi.push.service.MIPushEventProcessor.buildIntent;
import static com.xiaomi.push.service.MiPushMsgAck.sendAckMessage;
import static com.xiaomi.push.service.MiPushMsgAck.sendAppAbsentAck;
import static com.xiaomi.push.service.MiPushMsgAck.sendAppNotInstallNotification;
import static com.xiaomi.push.service.MiPushMsgAck.sendErrorAck;
import static com.xiaomi.push.service.MiPushMsgAck.shouldSendBroadcast;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.channel.commonutils.android.AppInfoUtils;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.reflect.JavaCalls;
import com.xiaomi.push.clientreport.PerfMessageHelper;
import com.xiaomi.push.service.clientReport.PushClientReportManager;
import com.xiaomi.push.service.clientReport.ReportConstants;
import com.xiaomi.smack.util.TrafficUtils;
import com.xiaomi.xmpush.thrift.ActionType;
import com.xiaomi.xmpush.thrift.NotificationType;
import com.xiaomi.xmpush.thrift.PushMetaInfo;
import com.xiaomi.xmpush.thrift.XmPushActionContainer;
import com.xiaomi.xmpush.thrift.XmPushActionNotification;
import com.xiaomi.xmsf.R;

import org.apache.thrift.TBase;
import org.apache.thrift.TException;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import top.trumeet.common.cache.ApplicationNameCache;
import top.trumeet.common.db.RegisteredApplicationDb;
import top.trumeet.common.register.RegisteredApplication;


/**
 *
 * @author zts1993
 * @date 2018/2/8
 */

public class MyMIPushMessageProcessor {
    private static Logger logger = XLog.tag("MyMIPushMessageProcessor").build();

    public static void process(XMPushService pushService, byte[] decryptedContent, long packetBytesLen) {
        Map<String, String> extra;
        XmPushActionContainer container = buildContainer(decryptedContent);
        if (container == null) {
            return;
        }
        if (TextUtils.isEmpty(container.packageName)) {
            MyLog.w("receive a mipush message without package name");
            return;
        }
        Long receiveTime = Long.valueOf(System.currentTimeMillis());
        Intent intent = buildIntent(decryptedContent, receiveTime.longValue());
        String realTargetPackage = MIPushNotificationHelper.getTargetPackage(container);
        TrafficUtils.distributionTraffic(pushService, realTargetPackage, packetBytesLen, true, true, System.currentTimeMillis());
        PushMetaInfo metaInfo = container.getMetaInfo();
        if (metaInfo != null && metaInfo.getId() != null) {
            MyLog.persist(String.format("receive a message. appid=%1$s, msgid= %2$s, action=%3$s", container.getAppid(), metaInfo.getId(), container.getAction()));
        }
        if (metaInfo != null) {
            metaInfo.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, Long.toString(receiveTime.longValue()));
        }
        if (ActionType.SendMessage == container.getAction() && MIPushAppInfo.getInstance(pushService).isUnRegistered(container.packageName) && !MIPushNotificationHelper.isBusinessMessage(container)) {
            String msgId = "";
            if (metaInfo != null) {
                msgId = metaInfo.getId();
                if (MIPushNotificationHelper.isNPBMessage(container)) {
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), msgId, "1");
                }
            }
            MyLog.w("Drop a message for unregistered, msgid=" + msgId);
            sendAppAbsentAck(pushService, container, container.packageName);
        } else if (ActionType.SendMessage == container.getAction() && MIPushAppInfo.getInstance(pushService).isPushDisabled4User(container.packageName) && !MIPushNotificationHelper.isBusinessMessage(container)) {
            String msgId2 = "";
            if (metaInfo != null) {
                msgId2 = metaInfo.getId();
                if (MIPushNotificationHelper.isNPBMessage(container)) {
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), msgId2, "2");
                }
            }
            MyLog.w("Drop a message for push closed, msgid=" + msgId2);
            sendAppAbsentAck(pushService, container, container.packageName);
        } else if (ActionType.SendMessage == container.getAction() && !TextUtils.equals(pushService.getPackageName(), PushConstants.PUSH_SERVICE_PACKAGE_NAME) && !TextUtils.equals(pushService.getPackageName(), container.packageName)) {
            MyLog.w("Receive a message with wrong package name, expect " + pushService.getPackageName() + ", received " + container.packageName);
            sendErrorAck(pushService, container, "unmatched_package", "package should be " + pushService.getPackageName() + ", but got " + container.packageName);
            if (metaInfo != null && MIPushNotificationHelper.isNPBMessage(container)) {
                PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "3");
            }
        } else if (metaInfo != null && (extra = metaInfo.getExtra()) != null && extra.containsKey("hide") && "true".equalsIgnoreCase(extra.get("hide"))) {
            sendAckMessage(pushService, container);
        } else {
            if (metaInfo != null && metaInfo.getExtra() != null && metaInfo.getExtra().containsKey(PushConstants.EXTRA_PARAM_MIID)) {
                String miidMsg = metaInfo.getExtra().get(PushConstants.EXTRA_PARAM_MIID);
                String miidSystem = SystemUtils.getMIID(pushService.getApplicationContext());
                if (TextUtils.isEmpty(miidSystem) || !TextUtils.equals(miidMsg, miidSystem)) {
                    if (MIPushNotificationHelper.isNPBMessage(container)) {
                        PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "4");
                    }
                    MyLog.w(miidMsg + " should be login, but got " + miidSystem);
                    sendErrorAck(pushService, container, "miid already logout or anther already login", miidMsg + " should be login, but got " + miidSystem);
                    return;
                }
            }
            postProcessMIPushMessage(pushService, realTargetPackage, decryptedContent, intent);
        }
    }


    /**
     * @see MIPushEventProcessor#postProcessMIPushMessage
     */
    private static void postProcessMIPushMessage(XMPushService pushService, String realTargetPackage, byte[] decryptedContent, Intent intent) {
        String str = null;
        String str2;
        boolean isDupMessage;
        String key;
        String str3;
        XmPushActionContainer container = buildContainer(decryptedContent);
        PushMetaInfo metaInfo = container.getMetaInfo();
        if (decryptedContent != null) {
            PerfMessageHelper.collectPerfData(container.getPackageName(), pushService.getApplicationContext(), null, container.getAction(), decryptedContent.length);
        }
        try {
            if (JavaCalls.<Boolean>callStaticMethodOrThrow(MIPushEventProcessor.class, "isMIUIOldAdsSDKMessage", container) &&
                    JavaCalls.<Boolean>callStaticMethodOrThrow(MIPushEventProcessor.class, "isMIUIPushSupported", pushService, realTargetPackage)) {
                if (MIPushNotificationHelper.isNPBMessage(container)) {
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "5");
                }
                JavaCalls.callStaticMethodOrThrow(MIPushEventProcessor.class, "sendMIUIOldAdsAckMessage", pushService, container);
            } else if (JavaCalls.<Boolean>callStaticMethodOrThrow(MIPushEventProcessor.class, "isMIUIPushMessage", container) &&
                    !JavaCalls.<Boolean>callStaticMethodOrThrow(MIPushEventProcessor.class, "isMIUIPushSupported", pushService, realTargetPackage) &&
                    !JavaCalls.<Boolean>callStaticMethodOrThrow(MIPushEventProcessor.class, "predefinedNotification", container)) {
                if (MIPushNotificationHelper.isNPBMessage(container)) {
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "6");
                }
                JavaCalls.callStaticMethodOrThrow(MIPushEventProcessor.class, "sendMIUINewAdsAckMessage", pushService, container);
            } else if ((!MIPushNotificationHelper.isBusinessMessage(container) || !AppInfoUtils.isPkgInstalled(pushService, container.packageName)) &&
                    !JavaCalls.<Boolean>callStaticMethodOrThrow(MIPushEventProcessor.class, "isIntentAvailable", pushService, intent)) {
                if (!AppInfoUtils.isPkgInstalled(pushService, container.packageName)) {
                    if (MIPushNotificationHelper.isNPBMessage(container)) {
                        PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4ERROR(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "2");
                    }
                    sendAppNotInstallNotification(pushService, container);
                    return;
                }
                MyLog.w("receive a mipush message, we can see the app, but we can't see the receiver.");
                if (MIPushNotificationHelper.isNPBMessage(container)) {
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4ERROR(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "3");
                }
            } else {
                if (ActionType.Registration == container.getAction()) {
                    String pkgName = container.getPackageName();
                    SharedPreferences sp = pushService.getSharedPreferences(PushServiceConstants.PREF_KEY_REGISTERED_PKGS, 0);
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString(pkgName, container.appid);
                    editor.commit();
                    MIPushAppInfo.getInstance(pushService).removeDisablePushPkg(pkgName);
                    MIPushAppInfo.getInstance(pushService).removeDisablePushPkgCache(pkgName);
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent(pkgName, ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID, metaInfo.getId(), ReportConstants.REGISTER_TYPE_RECEIVE, null);
                    if (!TextUtils.isEmpty(metaInfo.getId())) {
                        intent.putExtra("messageId", metaInfo.getId());
                        intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, ReportConstants.REGISTER_TYPE);
                    }
                }
                if (MIPushNotificationHelper.isNormalNotificationMessage(container)) {
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), 1001, System.currentTimeMillis(), null);
                    if (!TextUtils.isEmpty(metaInfo.getId())) {
                        intent.putExtra("messageId", metaInfo.getId());
                        intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, 1000);
                    }
                }
                if (MIPushNotificationHelper.isPassThoughMessage(container)) {
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), 2001, System.currentTimeMillis(), null);
                    if (!TextUtils.isEmpty(metaInfo.getId())) {
                        intent.putExtra("messageId", metaInfo.getId());
                        intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, 2000);
                    }
                }
                if (MIPushNotificationHelper.isBusinessMessage(container)) {
                    PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), 3001, System.currentTimeMillis(), null);
                    if (!TextUtils.isEmpty(metaInfo.getId())) {
                        intent.putExtra("messageId", metaInfo.getId());
                        intent.putExtra(ReportConstants.EVENT_MESSAGE_TYPE, ReportConstants.AWAKE_TYPE);
                    }
                }

                String title = metaInfo.getTitle();
                String description = metaInfo.getDescription();

                if (TextUtils.isEmpty(title) && TextUtils.isEmpty(description)) {
                } else {
                    if (TextUtils.isEmpty(title)) {
                        CharSequence appName = ApplicationNameCache.getInstance().getAppName(pushService, realTargetPackage);
                        metaInfo.setTitle(appName.toString());
                    }

                    if (TextUtils.isEmpty(description)) {
                        metaInfo.setDescription(pushService.getString(R.string.see_pass_though_msg));
                    }
                }

                RegisteredApplication application = RegisteredApplicationDb.registerApplication(
                        realTargetPackage, false, pushService, null);
                if (metaInfo == null || TextUtils.isEmpty(metaInfo.getTitle()) ||
                        TextUtils.isEmpty(metaInfo.getDescription()) || (metaInfo.passThrough == 1 && !application.isShowPassThrough()) ||
                        !MIPushNotificationHelper.isNotifyForeground(metaInfo.getExtra()) && MIPushNotificationHelper.isApplicationForeground(pushService, container.packageName)) {
                    str = PushConstants.PUSH_SERVICE_PACKAGE_NAME;
                } else {
                    String key2 = null;
                    if (metaInfo == null) {
                        isDupMessage = false;
                        key = null;
                    } else {
                        if (metaInfo.extra != null) {
                            String jobkey = metaInfo.extra.get(PushConstants.EXTRA_JOB_KEY);
                            key2 = jobkey;
                        }
                        if (TextUtils.isEmpty(key2)) {
                            key2 = metaInfo.getId();
                        }
                        boolean isDupMessage2 = MiPushMessageDuplicate.isDuplicateMessage(pushService, container.packageName, key2);
                        isDupMessage = isDupMessage2;
                        key = key2;
                    }
                    if (isDupMessage) {
                        PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4DUPMD(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "1:" + key);
                        MyLog.w("drop a duplicate message, key=" + key);
                        str3 = PushConstants.PUSH_SERVICE_PACKAGE_NAME;
                    } else {
                        MIPushNotificationHelper.NotifyPushMessageInfo info = MyMIPushNotificationHelper.notifyPushMessage(pushService, container, decryptedContent);
                        if (info.traffic <= 0 || TextUtils.isEmpty(info.targetPkgName)) {
                            str3 = PushConstants.PUSH_SERVICE_PACKAGE_NAME;
                        } else {
                            String str4 = info.targetPkgName;
                            long j = info.traffic;
                            long currentTimeMillis = System.currentTimeMillis();
                            str3 = PushConstants.PUSH_SERVICE_PACKAGE_NAME;
                            TrafficUtils.distributionTraffic(pushService, str4, j, true, false, currentTimeMillis);
                        }
                        if (!MIPushNotificationHelper.isBusinessMessage(container)) {
                            boolean appRunning = AppInfoUtils.isAppRunning(pushService.getApplicationContext(), realTargetPackage);
                            if (appRunning) {
                                Intent messageArrivedIntent = new Intent(PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED);
                                messageArrivedIntent.putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent);
                                messageArrivedIntent.setPackage(container.packageName);
                                try {
                                    PackageManager pm = pushService.getPackageManager();
                                    List<ResolveInfo> receiverList = pm.queryBroadcastReceivers(messageArrivedIntent, 0);
                                    if (receiverList != null && !receiverList.isEmpty()) {
                                        MyLog.w("broadcast message arrived.");
                                        pushService.sendBroadcast(messageArrivedIntent, MIPushHelper.getReceiverPermission(container.packageName));
                                    }
                                } catch (Exception e) {
                                    PushClientReportManager pcrm = PushClientReportManager.getInstance(pushService.getApplicationContext());
                                    pcrm.reportEvent4ERROR(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "1");
                                }
                            }
                        }
                    }
                    sendAckMessage(pushService, container);
                    str2 = str3;
                    if (container.getAction() != ActionType.UnRegistration && !str2.equals(pushService.getPackageName())) {
                        pushService.stopSelf();
                        return;
                    }
                }
                str2 = str;
                if (str2.contains(container.packageName) && !container.isEncryptAction() && metaInfo != null && metaInfo.getExtra() != null && metaInfo.getExtra().containsKey("ab")) {
                    sendAckMessage(pushService, container);
                    MyLog.v("receive abtest message. ack it." + metaInfo.getId());
                } else {
                    boolean shouldSendBroadcast = shouldSendBroadcast(pushService, realTargetPackage, container, metaInfo);
                    if (shouldSendBroadcast) {
                        if (metaInfo != null && !TextUtils.isEmpty(metaInfo.getId())) {
                            if (MIPushNotificationHelper.isPassThoughMessage(container)) {
                                PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), ReportConstants.THROUGH_TYPE_SEND_RECEIVE_BROADCAST, null);
                            } else if (MIPushNotificationHelper.isBusinessMessage(container)) {
                                PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "7");
                            } else if (MIPushNotificationHelper.isNormalNotificationMessage(container)) {
                                PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "8");
                            } else if (MIPushNotificationHelper.isRegisterMessage(container)) {
                                PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent(container.getPackageName(), ReportConstants.REGISTER_EVENT_CHAIN_INTERFACE_ID, metaInfo.getId(), ReportConstants.REGISTER_TYPE_SEND_BROADCAST, null);
                            }
                        }
                        boolean sendBroadcast = true;
                        if (ActionType.Notification == container.action) {
                            TBase message = null;
                            boolean success = false;
                            try {
                                message = UnEncryptedPushContainerHelper.getResponseMessageBodyFromContainer(pushService, container);
                                if (message == null) {
                                    MyLog.e("receiving an un-recognized notification message. " + container.action);
                                } else {
                                    success = true;
                                }
                            } catch (TException e2) {
                                MyLog.e("receive a message which action string is not valid. " + e2);
                            }
                            if (success && (message instanceof XmPushActionNotification)) {
                                XmPushActionNotification notification = (XmPushActionNotification) message;
                                if (NotificationType.CancelPushMessage.value.equals(notification.type) && notification.getExtra() != null) {
                                    int notifyId = -2;
                                    String notifyIdStr = notification.getExtra().get(PushConstants.PUSH_NOTIFY_ID);
                                    if (!TextUtils.isEmpty(notifyIdStr)) {
                                        try {
                                            notifyId = Integer.parseInt(notifyIdStr);
                                        } catch (NumberFormatException e3) {
                                            MyLog.w("parse notifyId from STRING to INT failed: " + e3);
                                            notifyId = -2;
                                        }
                                    }
                                    if (notifyId >= -1) {
                                        MyLog.w("try to retract a message by notifyId=" + notifyId);
                                        MIPushNotificationHelper.clearNotification(pushService, container.packageName, notifyId);
                                    } else {
                                        String retractTitle = notification.getExtra().get(PushConstants.PUSH_TITLE);
                                        String retractDescription = notification.getExtra().get(PushConstants.PUSH_DESCRIPTION);
                                        MyLog.w("try to retract a message by title&description.");
                                        MIPushNotificationHelper.clearNotification(pushService, container.packageName, retractTitle, retractDescription);
                                    }
                                    sendBroadcast = false;
                                    JavaCalls.callStaticMethodOrThrow(MIPushEventProcessor.class, "sendClearPushMessageAck", pushService, container, notification);
                                }
                            }
                        }
                        if (sendBroadcast) {
                            MyLog.w("broadcast passthrough message.");
                            pushService.sendBroadcast(intent, MIPushHelper.getReceiverPermission(container.packageName));
                        }
                    } else {
                        PushClientReportManager.getInstance(pushService.getApplicationContext()).reportEvent4NeedDrop(container.getPackageName(), MIPushNotificationHelper.getInterfaceId(container), metaInfo.getId(), "9");
                    }
                }
                if (container.getAction() != ActionType.UnRegistration) {
                }
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }


}
