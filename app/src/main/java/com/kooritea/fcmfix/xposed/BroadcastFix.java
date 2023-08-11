package com.kooritea.fcmfix.xposed;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.kooritea.fcmfix.R;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BroadcastFix extends XposedModule {
    private Process suProc;
    private DataOutputStream suProcOutputStream;
    private final String CGROUP_FROZEN_PROCS_PATH = "/sys/fs/cgroup/frozen/cgroup.procs";
    private final String CGROUP_UNFROZEN_PROCS_PATH = "/sys/fs/cgroup/unfrozen/cgroup.procs";
    private int currentHandledPID;
    private boolean isUnfrozen = false;

    public BroadcastFix(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        super(loadPackageParam);
        try {
            this.suProc = Runtime.getRuntime().exec("su");
            this.suProcOutputStream = new DataOutputStream(this.suProc.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onCanReadConfig() {
        this.startHook();
    }

    private String readExecResult(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = is.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return baos.toString("UTF-8");
    }

    private String suExecCommand(String command) throws IOException, InterruptedException {
        this.suProcOutputStream.writeBytes(command);
        this.suProcOutputStream.flush();
        this.suProc.waitFor();
        return readExecResult(this.suProc.getInputStream());
    }

    private boolean isProcessFrozen(int pid) {
        try {
            String result = this.suExecCommand(String.format("grep -w %d %s", pid, this.CGROUP_FROZEN_PROCS_PATH));
            // TODO: handle possible NullPointerException
            return Integer.getInteger(result) == pid;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void freezeProcess(int pid) {
        // TODO: make sure the process is unfrozen before freezing it
        try {
            this.suExecCommand(String.format("echo %d > %d", pid, this.CGROUP_FROZEN_PROCS_PATH));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void unfreezeProcess(int pid) {
        // TODO: make sure the process is frozen before unfreezing it
        try {
            this.suExecCommand(String.format("echo %d > %d", pid, this.CGROUP_UNFROZEN_PROCS_PATH));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getAppNameByPID(Context context, int pid){
        ActivityManager manager
                = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        for(ActivityManager.RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()){
            if(processInfo.pid == pid){
                return processInfo.processName;
            }
        }
        return "";
    }

    private List<String> getPkgListByPkgName(String packageName) {
        return new ArrayList<>();
    }

//    private L

    protected void startHook(){
        Class<?> clazz = XposedHelpers.findClass("com.android.server.am.ActivityManagerService",loadPackageParam.classLoader);
        final Method[] declareMethods = clazz.getDeclaredMethods();
        Method targetMethod = null;
        for(Method method : declareMethods){
            if("broadcastIntentLocked".equals(method.getName())){
                if(targetMethod == null || targetMethod.getParameterTypes().length < method.getParameterTypes().length){
                    targetMethod = method;
                }
            }
        }
        if(targetMethod != null){
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
                printLog("不支持的安卓版本(<10)，fcmfix将不会工作。");
                return;
            }
            int intent_args_index = 0;
            int appOp_args_index = 0;
            Parameter[] parameters = targetMethod.getParameters();
            if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q){
                intent_args_index = 2;
                appOp_args_index = 9;
            }else if(Build.VERSION.SDK_INT == Build.VERSION_CODES.R){
                intent_args_index = 3;
                appOp_args_index = 10;
            }else if(Build.VERSION.SDK_INT == 31){
                intent_args_index = 3;
                if(parameters[11].getType() == int.class){
                    appOp_args_index = 11;
                }
                if(parameters[12].getType() == int.class){
                    appOp_args_index = 12;
                }
            }else if(Build.VERSION.SDK_INT == 32){
                intent_args_index = 3;
                if(parameters[11].getType() == int.class){
                    appOp_args_index = 11;
                }
                if(parameters[12].getType() == int.class){
                    appOp_args_index = 12;
                }
            }else if(Build.VERSION.SDK_INT == 33){
                intent_args_index = 3;
                appOp_args_index = 12;
            }
            if(intent_args_index == 0 || appOp_args_index == 0){
                intent_args_index = 0;
                appOp_args_index = 0;
                // 根据参数名称查找，部分经过混淆的系统无效
                for(int i = 0; i < parameters.length; i++){
                    if("appOp".equals(parameters[i].getName()) && parameters[i].getType() == int.class){
                        appOp_args_index = i;
                    }
                    if("intent".equals(parameters[i].getName()) && parameters[i].getType() == Intent.class){
                        intent_args_index = i;
                    }
                }
            }
            if(intent_args_index == 0 || appOp_args_index == 0){
                intent_args_index = 0;
                appOp_args_index = 0;
                // 尝试用最后一个版本
                if(parameters[3].getType() == Intent.class && parameters[12].getType() == int.class){
                    intent_args_index = 3;
                    appOp_args_index = 12;
                    printLog("未适配的安卓版本，正在使用最后一个适配的安卓版本的配置，可能会出现工作异常。");
                }
            }
            if(intent_args_index == 0 || appOp_args_index == 0){
                intent_args_index = 0;
                appOp_args_index = 0;
                for(int i = 0; i < parameters.length; i++){
                    // 从最后一个适配的版本的位置左右查找appOp的位置
                    if(Math.abs(12-i) < 2 && parameters[i].getType() == int.class){
                        appOp_args_index = i;
                    }
                    // 唯一一个Intent参数的位置
                    if(parameters[i].getType() == Intent.class){
                        if(intent_args_index != 0){
                            printLog("查找到多个Intent，停止查找hook位置。");
                            intent_args_index = 0;
                            break;
                        }
                        intent_args_index = i;
                    }
                }
                if(intent_args_index != 0 && appOp_args_index != 0){
                    printLog("当前hook位置通过模糊查找得出，fcmfix可能不会正常工作。");
                }
            }
            printLog("Android API: " + Build.VERSION.SDK_INT);
            printLog("appOp_args_index: " + appOp_args_index);
            printLog("intent_args_index: " + intent_args_index);
            if(intent_args_index == 0 || appOp_args_index == 0){
                printLog("broadcastIntentLocked hook 位置查找失败，fcmfix将不会工作。");
                return;
            }
            final int finalIntent_args_index = intent_args_index;
            final int finalAppOp_args_index = appOp_args_index;

            XposedBridge.hookMethod(targetMethod,new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) methodHookParam.args[finalIntent_args_index];
                    if(intent != null && intent.getPackage() != null && intent.getFlags() != Intent.FLAG_INCLUDE_STOPPED_PACKAGES && "com.google.android.c2dm.intent.RECEIVE".equals(intent.getAction())){
                        String target;
                        if (intent.getComponent() != null) {
                            target = intent.getComponent().getPackageName();
                        } else {
                            target = intent.getPackage();
                        }

//                        // check if the process is frozen（support FreezeIt v2 Frozen mode currently)
//                        pkgList = getPkgListByPkgName(pkgName);
//                        processList = getProcessListByPkgList(pkgList);
                        printLog("Receive message for [" + target + "]");

                        if(targetIsAllow(target)){
                            int i = (Integer) methodHookParam.args[finalAppOp_args_index];
                            if (i == -1) {
                                methodHookParam.args[finalAppOp_args_index] = 11;
                            }
                            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            printLog("Send Forced Start Broadcast: " + target);
                        }
                    }
                }
            });
        }
    }
}
