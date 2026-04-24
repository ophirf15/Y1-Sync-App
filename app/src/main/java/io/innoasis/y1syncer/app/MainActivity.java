package io.innoasis.y1syncer.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
    private final List<View> dpadFocusOrder = new ArrayList<View>();
    private boolean autoSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        runtimeController = ((RuntimeApplication) getApplication()).getRuntimeController();
        txtIpTop = findViewById(R.id.txtIpTop);
        txtStatus = findViewById(R.id.txtStatus);
        editServerPort = findViewById(R.id.editServerPort);
        editServerPort.setText(String.valueOf(runtimeController.getServerPort()));

        bindButtons();
        configureDpadNavigation();
        refreshStatus();
    }

    private void bindButtons() {
        Button btnWifi = findViewById(R.id.btnWifi);
        Button btnSetPort = findViewById(R.id.btnSetPort);
        Button btnStartServer = findViewById(R.id.btnStartServer);
        Button btnStopServer = findViewById(R.id.btnStopServer);
        Button btnSyncNow = findViewById(R.id.btnSyncNow);
        Button btnAutoSync = findViewById(R.id.btnAutoSync);
        Button btnCheckUpdates = findViewById(R.id.btnCheckUpdates);
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
                runtimeController.syncNow("manual");
                refreshStatus();
            }
        });
        btnAutoSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoSync = !autoSync;
                runtimeController.setAutoSyncEnabled(autoSync);
                refreshStatus();
            }
        });
        btnCheckUpdates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    runtimeController.checkForBundleUpdates();
                    toast("Update check completed");
                } catch (Exception e) {
                    toast("Update check failed");
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
        txtIpTop.setText("IP: " + s.localIp + ":" + s.serverPort);
        String status = "Server: " + (s.serverRunning ? "ON" : "OFF")
                + "\nAddress: http://" + s.localIp + ":" + s.serverPort
                + "\nAuto-sync: " + s.autoSyncEnabled
                + "\nProfile: " + s.currentProfile
                + "\nLast sync: " + s.lastSyncStatus
                + "\nStorage: " + s.storageSummary
                + "\nBattery: " + s.batterySummary
                + "\nUpdate: " + s.updateStatus;
        txtStatus.setText(status);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
