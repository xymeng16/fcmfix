package com.kooritea.fcmfix.xposed;

import static android.content.Context.ACTIVITY_SERVICE;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import com.kooritea.fcmfix.BuildConfig;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import com.topjohnwu.superuser.Shell;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BroadcastFix extends XposedModule {
    static {
        // Set settings before the main shell can be created
        Shell.enableVerboseLogging = BuildConfig.DEBUG;
        Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        );
    }

    private final String CGROUP_FROZEN_PROCS_PATH = "/sys/fs/cgroup/frozen/cgroup.procs";
    private final String CGROUP_UNFROZEN_PROCS_PATH = "/sys/fs/cgroup/unfrozen/cgroup.procs";
    private int currentHandledPID;
    private boolean isUnfrozen = false;

    public BroadcastFix(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        super(loadPackageParam);
        Shell.getShell(shell -> printLog("super user initialized", false));
    }

    @Override
    protected void onCanReadConfig() {
        this.startHook();
    }

    private List<String> suExecCommand(String command) {
        Shell.Result result;
        Log.d("LIBSU_EXEC", "suExecCommand: " + command);
        result = Shell.cmd(command).exec();
        Log.d("LIBSU_EXEC", "result: " + result);
        return result.getOut();  // stdout
    }

    private boolean isProcessFrozen(int pid) {
        List<String> result = this.suExecCommand("grep -w " + pid + " " + this.CGROUP_FROZEN_PROCS_PATH);
        return result.size() != 0;
    }

    private void freezeProcess(int pid) {
        // TODO: make sure the process is unfrozen before freezing it
        this.suExecCommand("echo " + pid + " > " + this.CGROUP_FROZEN_PROCS_PATH);

    }

    private void unfreezeProcess(int pid) {
        // TODO: make sure the process is frozen before unfreezing it
        this.suExecCommand(String.format("echo %d > %d", pid, this.CGROUP_UNFROZEN_PROCS_PATH));
    }

    @SuppressLint("PrivateApi")
    private int[] getUserIdList() {
        UserManager userManager = (UserManager) context.getSystemService(UserManager.class);
        // use reflection to invoke UserManager.getUsers() as it is hidden
        try {
            Class<?> UserInfo = Class.forName("android.content.pm.UserInfo");
            Method method = userManager.getClass().getMethod("getUsers", null);
            List<Object> users = (List<Object>) method.invoke(userManager, null); // List<UserInfo>
            int[] results = new int[users.size()];
            Field uid = UserInfo.getDeclaredField("id");
            uid.setAccessible(true);
            int count = 0;
            for (Object o: users) {
                results[count++] = uid.getInt(o);
            }
            if (count != results.length) {
                throw new RuntimeException("UserInfo list size mismatch");
            }
            return results;
        } catch (InvocationTargetException | IllegalAccessException | ClassNotFoundException |
                 NoSuchMethodException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private boolean isPackageValid(String packageName, int uid) {
        PackageManager pm = context.getPackageManager();
        try {
            // TODO: do we need double-check?
            return pm.getPackageUid(packageName, 0) == uid;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getPkgListByPkgName(String packageName) {
        List<String> packageList;
        int[] uids = getUserIdList();
        for (int i = 0; i < uids.length; i++) {

        }
        return new ArrayList<>();
    }

    private List<ActivityManager.RunningAppProcessInfo> getProcessListByPkgName(String packageName) {
        List<ActivityManager.RunningAppProcessInfo> result = new ArrayList<>();
        ActivityManager activityManager = (ActivityManager) context.getSystemService( ACTIVITY_SERVICE );
        List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        for (int iCnt = 0; iCnt < procInfos.size(); iCnt++){
            if (procInfos.get(iCnt).processName.contains(packageName)){
                result.add(procInfos.get(iCnt));
            }
        }
        return result;
    }

    private void frozenLog(String log) {
        printLog("\uD83D\uDE80 frozen-fix: " + log);
    }

    protected void startHook() {
        Class<?> clazz = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", loadPackageParam.classLoader);
        final Method[] declareMethods = clazz.getDeclaredMethods();
        Method targetMethod = null;
        for (Method method : declareMethods) {
            if ("broadcastIntentLocked".equals(method.getName())) {
                if (targetMethod == null || targetMethod.getParameterTypes().length < method.getParameterTypes().length) {
                    targetMethod = method;
                }
            }
        }
        if (targetMethod != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                printLog("不支持的安卓版本(<10)，fcmfix将不会工作。");
                return;
            }
            int intent_args_index = 0;
            int appOp_args_index = 0;
            Parameter[] parameters = targetMethod.getParameters();
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                intent_args_index = 2;
                appOp_args_index = 9;
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                intent_args_index = 3;
                appOp_args_index = 10;
            } else if (Build.VERSION.SDK_INT == 31) {
                intent_args_index = 3;
                if (parameters[11].getType() == int.class) {
                    appOp_args_index = 11;
                }
                if (parameters[12].getType() == int.class) {
                    appOp_args_index = 12;
                }
            } else if (Build.VERSION.SDK_INT == 32) {
                intent_args_index = 3;
                if (parameters[11].getType() == int.class) {
                    appOp_args_index = 11;
                }
                if (parameters[12].getType() == int.class) {
                    appOp_args_index = 12;
                }
            } else if (Build.VERSION.SDK_INT == 33) {
                intent_args_index = 3;
                appOp_args_index = 12;
            }
            if (intent_args_index == 0 || appOp_args_index == 0) {
                intent_args_index = 0;
                appOp_args_index = 0;
                // 根据参数名称查找，部分经过混淆的系统无效
                for (int i = 0; i < parameters.length; i++) {
                    if ("appOp".equals(parameters[i].getName()) && parameters[i].getType() == int.class) {
                        appOp_args_index = i;
                    }
                    if ("intent".equals(parameters[i].getName()) && parameters[i].getType() == Intent.class) {
                        intent_args_index = i;
                    }
                }
            }
            if (intent_args_index == 0 || appOp_args_index == 0) {
                intent_args_index = 0;
                appOp_args_index = 0;
                // 尝试用最后一个版本
                if (parameters[3].getType() == Intent.class && parameters[12].getType() == int.class) {
                    intent_args_index = 3;
                    appOp_args_index = 12;
                    printLog("未适配的安卓版本，正在使用最后一个适配的安卓版本的配置，可能会出现工作异常。");
                }
            }
            if (intent_args_index == 0 || appOp_args_index == 0) {
                intent_args_index = 0;
                appOp_args_index = 0;
                for (int i = 0; i < parameters.length; i++) {
                    // 从最后一个适配的版本的位置左右查找appOp的位置
                    if (Math.abs(12 - i) < 2 && parameters[i].getType() == int.class) {
                        appOp_args_index = i;
                    }
                    // 唯一一个Intent参数的位置
                    if (parameters[i].getType() == Intent.class) {
                        if (intent_args_index != 0) {
                            printLog("查找到多个Intent，停止查找hook位置。");
                            intent_args_index = 0;
                            break;
                        }
                        intent_args_index = i;
                    }
                }
                if (intent_args_index != 0 && appOp_args_index != 0) {
                    printLog("当前hook位置通过模糊查找得出，fcmfix可能不会正常工作。");
                }
            }
            printLog("Android API: " + Build.VERSION.SDK_INT);
            printLog("appOp_args_index: " + appOp_args_index);
            printLog("intent_args_index: " + intent_args_index);
            if (intent_args_index == 0 || appOp_args_index == 0) {
                printLog("broadcastIntentLocked hook 位置查找失败，fcmfix将不会工作。");
                return;
            }
            final int finalIntent_args_index = intent_args_index;
            final int finalAppOp_args_index = appOp_args_index;

            XposedBridge.hookMethod(targetMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) methodHookParam.args[finalIntent_args_index];
                    if (intent != null && intent.getPackage() != null && intent.getFlags() != Intent.FLAG_INCLUDE_STOPPED_PACKAGES && "com.google.android.c2dm.intent.RECEIVE".equals(intent.getAction())) {
                        String target;
                        if (intent.getComponent() != null) {
                            target = intent.getComponent().getPackageName();
                        } else {
                            target = intent.getPackage();
                        }

                        // check if the process is frozen（support FreezeIt v2 Frozen mode currently)
                        // logic: packageName -> its uid -> all packageNames under this uid -> all pid
                        frozenLog("Frozen handling starts");
                        List<RunningAppProcessInfo> processList =
                                getProcessListByPkgName(target);
                        if (processList.size() != 0) {
                            // Assume uids of each process are identical
                            frozenLog("[" + target + "], uid: " + processList.get(0).uid);

                            List<Integer> pidList = new ArrayList<>();
                            for (RunningAppProcessInfo process: processList) {
                                pidList.add(process.pid);
                            }
                            frozenLog("Currently running process of [" + target + "]:");
                            for (int i = 0; i < processList.size(); i++) {
                                frozenLog(pidList.get(i).toString() + ":" + processList.get(i).processName);
                            }

                            // check which processes are currently frozen
                            List<Integer> frozenPidList = new ArrayList<>();
                            for (RunningAppProcessInfo process: processList) {
                                if (isProcessFrozen(process.pid)) {
                                    frozenPidList.add(process.pid);
                                }
                            }
                            if (frozenPidList.size() != 0) {
                                frozenLog("Currently frozen pid of [:" + target + "]:");
                                for (int i = 0; i < processList.size(); i++) {
                                    frozenLog(frozenPidList.get(i).toString());
                                }
                                frozenLog("try to unfreeze them");
                                for (int pid: frozenPidList) {
                                    unfreezeProcess(pid);
                                }
                                // TODO: re-freeze apps
                                frozenLog("unimplemented: re-freeze apps");
                            } else {
                                frozenLog("[" + target + "] is not frozen");
                            }
                        } else {
                            frozenLog("[" + target + "] is not running, ignore");
                        }
                        frozenLog("Frozen handling ends");
                        if (targetIsAllow(target)) {
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
