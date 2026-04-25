package io.innoasis.y1syncer.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.innoasis.y1syncer.R;
import io.innoasis.y1syncer.runtime.CoreRuntimeController;
import io.innoasis.y1syncer.runtime.RuntimeStatusSnapshot;

public class MainActivity extends Activity {
    private CoreRuntimeController runtimeController;
    private TextView txtIpTop;
    private TextView txtStatus;
    private EditText editServerPort;
    private Button btnSyncNow;
    private Button btnAutoSync;
    private Button btnCheckUpdates;
    private final List<View> dpadFocusOrder = new ArrayList<View>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private boolean autoSync;
    private long lastDownloadId = -1L;
    private final Runnable statusPoll = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            uiHandler.postDelayed(this, 1500L);
        }
    };
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id != lastDownloadId || id < 0) {
                return;
            }
            installDownloadedApk(id);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        runtimeController = ((RuntimeApplication) getApplication()).getRuntimeController();
        txtIpTop = findViewById(R.id.txtIpTop);
        txtStatus = findViewById(R.id.txtStatus);
        editServerPort = findViewById(R.id.editServerPort);
        editServerPort.setFocusable(true);
        editServerPort.setFocusableInTouchMode(true);
        editServerPort.setClickable(true);
        editServerPort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showKeyboard(v);
            }
        });
        editServerPort.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    showKeyboard(v);
                }
            }
        });
        editServerPort.setText(String.valueOf(runtimeController.getServerPort()));
        autoSync = runtimeController.isAutoSyncEnabled();

        bindButtons();
        configureDpadNavigation();
        refreshStatus();
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        uiHandler.post(statusPoll);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(statusPoll);
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {
        }
    }

    private void bindButtons() {
        Button btnWifi = findViewById(R.id.btnWifi);
        Button btnSetPort = findViewById(R.id.btnSetPort);
        Button btnStartServer = findViewById(R.id.btnStartServer);
        Button btnStopServer = findViewById(R.id.btnStopServer);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        btnAutoSync = findViewById(R.id.btnAutoSync);
        btnCheckUpdates = findViewById(R.id.btnCheckUpdates);
        Button btnRevertUpdates = findViewById(R.id.btnRevertUpdates);

        btnWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        });
        btnSetPort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String raw = editServerPort.getText().toString().trim();
                if (raw.length() == 0) {
                    toast("Enter a port");
                    return;
                }
                try {
                    int parsed = Integer.parseInt(raw);
                    runtimeController.setServerPort(parsed);
                    toast("Port set to " + parsed);
                    refreshStatus();
                } catch (Exception e) {
                    toast("Invalid port");
                }
            }
        });
        btnStartServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    runtimeController.startServer();
                    toast("Server started");
                } catch (Exception e) {
                    toast("Start failed: " + e.getMessage());
                }
                refreshStatus();
            }
        });
        btnStopServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runtimeController.stopServer();
                refreshStatus();
            }
        });
        btnSyncNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSyncNow.setEnabled(false);
                runtimeController.syncNow("manual");
            }
        });
        btnAutoSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoSync = !autoSync;
                runtimeController.setAutoSyncEnabled(autoSync);
                toast("Auto-sync " + (autoSync ? "ON" : "OFF"));
            }
        });
        btnCheckUpdates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    JSONObject updates = runtimeController.getUpdatesStatusJson();
                    String apkUrl = updates.optString("apk_download_url", "");
                    String releaseUrl = updates.optString("latest_release_url", "");
                    if (updates.optBoolean("apk_update_available", false) && apkUrl.length() > 0) {
                        startApkDownload(apkUrl);
                    } else if (releaseUrl.length() > 0) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)));
                    } else {
                        runtimeController.checkForBundleUpdates();
                        toast("No newer APK found. Web bundle check completed.");
                    }
                } catch (Exception e) {
                    toast("Update check failed: " + e.getMessage());
                }
            }
        });
        btnRevertUpdates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runtimeController.revertBundledUi();
                toast("Reverted to bundled web UI");
            }
        });

        dpadFocusOrder.clear();
        dpadFocusOrder.add(btnWifi);
        dpadFocusOrder.add(btnSetPort);
        dpadFocusOrder.add(btnStartServer);
        dpadFocusOrder.add(btnStopServer);
        dpadFocusOrder.add(btnSyncNow);
        dpadFocusOrder.add(btnAutoSync);
        dpadFocusOrder.add(btnCheckUpdates);
        dpadFocusOrder.add(btnRevertUpdates);
    }

    private void configureDpadNavigation() {
        for (View control : dpadFocusOrder) {
            control.setFocusable(true);
            control.setFocusableInTouchMode(true);
        }
        if (!dpadFocusOrder.isEmpty()) {
            dpadFocusOrder.get(0).requestFocus();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (dpadFocusOrder.isEmpty()) {
                return super.onKeyDown(keyCode, event);
            }
            int current = findFocusedControlIndex();
            if (current < 0) {
                current = 0;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                current = (current + 1) % dpadFocusOrder.size();
            } else {
                current = (current - 1 + dpadFocusOrder.size()) % dpadFocusOrder.size();
            }
            dpadFocusOrder.get(current).requestFocus();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int findFocusedControlIndex() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return -1;
        }
        for (int i = 0; i < dpadFocusOrder.size(); i++) {
            if (dpadFocusOrder.get(i).getId() == focused.getId()) {
                return i;
            }
        }
        return -1;
    }

    private void refreshStatus() {
        RuntimeStatusSnapshot s = runtimeController.getStatusSnapshot();
        io.innoasis.y1syncer.runtime.SyncStatusState sync = runtimeController.getSyncStatusStateCopy();
        txtIpTop.setText("IP: " + s.localIp + ":" + s.serverPort);
        String status = "Server: " + (s.serverRunning ? "ON" : "OFF")
                + "\nAddress: http://" + s.localIp + ":" + s.serverPort
                + "\nAuto-sync: " + s.autoSyncEnabled
                + "\nProfile: " + s.currentProfile
                + "\nLast sync: " + s.lastSyncStatus;
        if ("running".equals(sync.state)) {
            int pct = sync.bytesTotal > 0 ? (int) ((100L * sync.bytesDone) / sync.bytesTotal) : 0;
            status += "\nSync progress: " + sync.currentIndex + "/" + sync.totalFiles + " (" + pct + "%)"
                    + "\nCurrent file: " + sync.currentFile;
            btnSyncNow.setEnabled(false);
        } else {
            btnSyncNow.setEnabled(true);
        }
        status += "\nStorage: " + s.storageSummary
                + "\nBattery: " + s.batterySummary
                + "\nUpdate: " + s.updateStatus;
        txtStatus.setText(status);
        btnAutoSync.setText("Auto Sync: " + (autoSync ? "ON" : "OFF"));
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void showKeyboard(View target) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            target.requestFocus();
            imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void startApkDownload(String apkUrl) {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                toast("Download manager unavailable");
                return;
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl));
            req.setTitle("Y1 Sync update");
            req.setDescription("Downloading app update");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "y1-sync-update.apk");
            lastDownloadId = dm.enqueue(req);
            toast("Downloading APK update...");
        } catch (Exception e) {
            toast("Download failed: " + e.getMessage());
        }
    }

    private void installDownloadedApk(long downloadId) {
        DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            return;
        }
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(downloadId);
        android.database.Cursor c = dm.query(q);
        if (c == null) {
            return;
        }
        try {
            if (!c.moveToFirst()) {
                return;
            }
            int statusCol = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int localUriCol = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
            if (statusCol < 0 || localUriCol < 0) {
                return;
            }
            int st = c.getInt(statusCol);
            String localUri = c.getString(localUriCol);
            if (st != DownloadManager.STATUS_SUCCESSFUL || localUri == null) {
                toast("APK download failed");
                return;
            }
            Uri source = Uri.parse(localUri);
            File apkFile = new File(source.getPath());
            Uri installUri;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 24) {
                installUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                installUri = Uri.fromFile(apkFile);
            }
            i.setDataAndType(installUri, "application/vnd.android.package-archive");
            startActivity(i);
        } catch (Exception e) {
            toast("Install failed: " + e.getMessage());
        } finally {
            c.close();
        }
    }
}
