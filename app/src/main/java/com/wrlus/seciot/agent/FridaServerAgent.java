package com.wrlus.seciot.agent;

import android.util.Log;

import com.wrlus.seciot.util.RootShellHelper;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class FridaServerAgent {
    private static FridaServerAgent instance;
    private String AGENT_SERVER = "http://140.143.52.29:8080/SecIoT";

    private FridaServerAgent() {
        Log.d("FridaServerAgent", "Create FridaServerAgent singleton");
    }

    public static FridaServerAgent getInstance() {
        if (instance == null) {
            synchronized (FridaServerAgent.class) {
                if (instance == null) {
                    instance = new FridaServerAgent();
                }
            }
        }
        return instance;
    }

    public void setAgentServer(String serverUrl) {
        AGENT_SERVER = serverUrl;
    }

    public void getFridaVersionOnServer(Callback callback) {
        String url = AGENT_SERVER + "/agent/frida-version";
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().get().url(url).build();
        okHttpClient.newCall(request).enqueue(callback);
    }

    public void downloadFridaServer(String version, String abi, Callback callback) {
        String url = AGENT_SERVER + "/attach/downloads/frida/${version}/".replace("${version}", version) +
                "frida-server-${version}-android-${abi}.tar.gz".replace("${version}", version).replace("${abi}", abi);
        OkHttpClient okHttpClient = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();
        okHttpClient.newCall(request).enqueue(callback);
    }

    public void installFridaServer(final File downloadFile, final String version, final StatusCallback callback) {
        String targetPath = "/data/local/tmp/seciot/frida/" + version + "/";
        final String[] cmds = {
                "mkdir /data/local/tmp/seciot/",
                "mkdir /data/local/tmp/seciot/frida/",
                "mkdir " + targetPath,
                "mv " + downloadFile.getAbsolutePath() + " " + targetPath,
                "cd " + targetPath,
                "tar -zxvf " + downloadFile.getName(),
                "rm " + downloadFile.getName(),
                "mv " + downloadFile.getName().replace(".tar.gz", "") + " frida-server",
                "chmod +x frida-server"
        };
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessBuilder processBuilder = new ProcessBuilder("su");
                    processBuilder.redirectErrorStream(true);
                    Process process = processBuilder.start();
                    BufferedReader bs = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    DataOutputStream os = new DataOutputStream(process.getOutputStream());
                    for (String cmd : cmds) {
                        Log.d("ExecCmd", cmd);
                        os.writeBytes( cmd + "\n");
                    }
                    os.writeBytes("exit\n");
                    os.flush();
                    process.waitFor();
                    String line;
                    while ((line = bs.readLine()) != null) {
                        Log.i("InstallFridaServer", line);
                    }
                    if (process.exitValue() != 0) {
                        callback.onFailure(process.exitValue(), null);
                        return;
                    }
                    callback.onSuccess();
                } catch (Exception e) {
                    callback.onFailure(-1, e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public boolean checkFridaServerInstallation(String version) {
        String targetPath = "/data/local/tmp/seciot/frida/" + version + "/";
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ls", targetPath + "frida-server");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader bs = new BufferedReader(new InputStreamReader(process.getInputStream()));
            process.waitFor();
            String line;
            while ((line = bs.readLine()) != null) {
                Log.i("FridaInstallationCheck", line);
            }
            if (process.exitValue() == 0) {
                return true;
            }
            Log.e("FridaInstallationCheck", String.valueOf(process.exitValue()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void removeFridaServer(final String version, final StatusCallback callback) {
        String targetPath = "/data/local/tmp/seciot/frida/" + version + "/";
        final String[] cmds = {
                "rm -rf " + targetPath
        };
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessBuilder processBuilder = new ProcessBuilder("su");
                    processBuilder.redirectErrorStream(true);
                    Process process = processBuilder.start();
                    BufferedReader bs = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    DataOutputStream os = new DataOutputStream(process.getOutputStream());
                    for (String cmd : cmds) {
                        Log.d("ExecCmd", cmd);
                        os.writeBytes( cmd + "\n");
                    }
                    os.writeBytes("exit\n");
                    os.flush();
                    process.waitFor();
                    String line;
                    while ((line = bs.readLine()) != null) {
                        Log.i("RemoveFridaServer", line);
                    }
                    if (process.exitValue() != 0) {
                        callback.onFailure(process.exitValue(), null);
                        return;
                    }
                    callback.onSuccess();
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onFailure(-1, e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void startFridaServer(String version) {
        String targetPath = "/data/local/tmp/seciot/frida/" + version + "/";
        String[] cmds = {
                "cd "+targetPath,
                "chmod +x frida-server",
                "./frida-server &"
        };
        RootShellHelper rootShellHelper = RootShellHelper.getInstance();
        try {
            rootShellHelper.execute(cmds);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopFridaServer() {
        RootShellHelper rootShellHelper = RootShellHelper.getInstance();
        try {
            rootShellHelper.execute("kill -9 $(pidof frida-server)");
            rootShellHelper.exit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
