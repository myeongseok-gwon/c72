package com.example.uhf.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.adapter.PickTaskAdapter;
import com.example.uhf.api.ApiClient;
import com.example.uhf.iot.AwsIotManager;
import com.example.uhf.response.ClosePickOrderResponse;
import com.example.uhf.response.PickOrderResponse;
import com.example.uhf.response.PickTask;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PickActivity extends AppCompatActivity implements PickTaskAdapter.OnScanButtonClickListener {

    private static final String TAG = "PickActivity";
    private TextView tvPickOrderId, tvPickerId, tvPickingZone, tvPickOrderStatus, tvOrderCreatedDate;
    private RecyclerView rvPickTasks;
    private ProgressBar progressBar;
    private Button btnCompletePick;
    private PickTaskAdapter adapter;
    private List<PickTask> pickTasks;
    private String pickOrderId;
    private String apiKey;
    private String employeeId;
    private String userRole;

    // RFID & IoT
    private RFIDWithUHFUART uhfReader;
    private AwsIotManager awsIotManager;
    private boolean isScanning = false;
    private int currentScanningPosition = -1;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick);

        tvPickOrderId = findViewById(R.id.tv_pick_order_id);
        tvPickerId = findViewById(R.id.tv_picker_id);
        tvPickingZone = findViewById(R.id.tv_picking_zone);
        tvPickOrderStatus = findViewById(R.id.tv_pick_order_status);
        tvOrderCreatedDate = findViewById(R.id.tv_order_created_date);
        rvPickTasks = findViewById(R.id.rv_pick_tasks);
        progressBar = findViewById(R.id.progress_bar);
        btnCompletePick = findViewById(R.id.btn_complete_pick);

        rvPickTasks.setLayoutManager(new LinearLayoutManager(this));

        apiKey = getIntent().getStringExtra("api_key");
        employeeId = getIntent().getStringExtra("employee_id");
        userRole = getIntent().getStringExtra("role");
        if (apiKey == null || apiKey.isEmpty()) {
            Toast.makeText(this, "API Key is missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (employeeId == null || employeeId.isEmpty() || userRole == null || userRole.isEmpty()) {
            Toast.makeText(this, "직원 정보가 누락되었습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeRFID();
        initializeAwsIot();
        setupClickListeners();
        fetchPickOrder(apiKey, employeeId, userRole);
    }

    private void setupClickListeners() {
        btnCompletePick.setOnClickListener(v -> {
            if (pickOrderId != null) {
                closePickOrder();
            }
        });
    }

    private void closePickOrder() {
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.closePickOrder(apiKey, pickOrderId, employeeId, userRole, new ApiClient.ClosePickOrderCallback() {
            @Override
            public void onSuccess(ClosePickOrderResponse response) {
                progressBar.setVisibility(View.GONE);
                showResultDialog(response);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(PickActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showResultDialog(ClosePickOrderResponse response) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pick Order Closed");
        StringBuilder message = new StringBuilder();
        message.append(response.getMessage());
        if (response.getPickSlipStatus() != null && !response.getPickSlipStatus().isEmpty()) {
            message.append("\n\nNew Pick Slip Status: ").append(response.getPickSlipStatus());
        }
        builder.setMessage(message.toString());
        builder.setPositiveButton("OK", (dialog, which) -> finish());
        builder.setOnDismissListener(dialog -> finish());
        builder.show();
    }

    private void initializeRFID() {
        try {
            uhfReader = RFIDWithUHFUART.getInstance();
            if (uhfReader != null) {
                if (uhfReader.init(this)) {
                    Log.i(TAG, "RFID 리더 초기화 성공");
                } else {
                    Log.e(TAG, "RFID 리더 초기화 실패");
                    Toast.makeText(this, "RFID Reader initialization failed", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "RFID 초기화 오류", e);
            Toast.makeText(this, "RFID reader initialization error", Toast.LENGTH_LONG).show();
        }
    }

    private void initializeAwsIot() {
        awsIotManager = new AwsIotManager(this);
        awsIotManager.connectWithCertificate(new AwsIotManager.ConnectionCallback() {
            @Override
            public void onConnectionSuccess() {
                runOnUiThread(() -> Toast.makeText(PickActivity.this, "AWS IoT Connected", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onConnectionFailure(Exception exception) {
                runOnUiThread(() -> Toast.makeText(PickActivity.this, "AWS IoT Connection Failed", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void fetchPickOrder(String apiKey, String employeeId, String userRole) {
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getNextPickOrder(apiKey, employeeId, userRole, new ApiClient.PickOrderCallback() {
            @Override
            public void onSuccess(PickOrderResponse response) {
                progressBar.setVisibility(View.GONE);
                updateUI(response);
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(PickActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateUI(PickOrderResponse response) {
        pickOrderId = response.getPickOrderId();
        tvPickOrderId.setText("Pick Order ID: " + pickOrderId);
        tvPickerId.setText("Picker ID: " + response.getPickerId());
        tvPickingZone.setText("Picking Zone: " + response.getPickingZone());
        tvPickOrderStatus.setText("Status: " + response.getPickOrderStatus());
        tvOrderCreatedDate.setText("Created: " + response.getOrderCreatedDate());

        pickTasks = response.getPickTask();
        adapter = new PickTaskAdapter(pickTasks, PickActivity.this);
        rvPickTasks.setAdapter(adapter);
    }

    @Override
    public void onScanButtonClick(int position) {
        if (isScanning) {
            stopScanning();
        } else {
            currentScanningPosition = position;
            startLoopScanning();
        }
    }

    private void startLoopScanning() {
        if (currentScanningPosition == -1) return;

        isScanning = true;
        PickTask currentTask = pickTasks.get(currentScanningPosition);
        currentTask.clearScanned(); // 스캔 시작 시 이전 데이터 초기화

        uhfReader.setInventoryCallback(uhftagInfo -> {
            handler.post(() -> handleTagScanned(uhftagInfo));
        });

        if (uhfReader.startInventoryTag()) {
            Toast.makeText(this, "Scanning for Bin: " + currentTask.getBinId(), Toast.LENGTH_SHORT).show();
            // UI 업데이트: 스캔중인 버튼의 텍스트 변경 등
            adapter.notifyItemChanged(currentScanningPosition);
        } else {
            Toast.makeText(this, "RFID Reader failed to start inventory.", Toast.LENGTH_SHORT).show();
            isScanning = false;
        }
    }

    private void stopScanning() {
        if (!isScanning) return;

        isScanning = false;
        uhfReader.stopInventory();
        Toast.makeText(this, "Scan stopped.", Toast.LENGTH_SHORT).show();
        // UI 업데이트
        adapter.notifyItemChanged(currentScanningPosition);
        currentScanningPosition = -1;
    }

    private void handleTagScanned(UHFTAGInfo tagInfo) {
        if (!isScanning || currentScanningPosition == -1) return;
        PickTask currentTask = pickTasks.get(currentScanningPosition);
        int requiredQuantity = 0;
        try {
            requiredQuantity = Integer.parseInt(currentTask.getQuantity());
        } catch (NumberFormatException e) {
            Log.e(TAG, "Could not parse quantity for task", e);
            return;
        }
        if (currentTask.getScannedQuantity() >= requiredQuantity) {
            return;
        }
        boolean isNew = currentTask.addScannedEpc(tagInfo.getEPC());
        if (isNew) {
            adapter.notifyItemChanged(currentScanningPosition);
            publishToAwsIot(tagInfo);
            checkAllTasksComplete();
        }
    }

    private void checkAllTasksComplete() {
        for (PickTask task : pickTasks) {
            try {
                int requiredQuantity = Integer.parseInt(task.getQuantity());
                if (task.getScannedQuantity() < requiredQuantity) {
                    btnCompletePick.setEnabled(false);
                    return;
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Could not parse quantity for task", e);
                btnCompletePick.setEnabled(false);
                return;
            }
        }
        btnCompletePick.setEnabled(true);
    }

    private void publishToAwsIot(UHFTAGInfo tagInfo) {
        if (awsIotManager == null || !awsIotManager.isConnected()) {
            Log.w(TAG, "AWS IoT not connected. Cannot publish.");
            return;
        }

        String topic = "rfid/po";
        try {
            JSONObject payload = new JSONObject();
            payload.put("pick_order_id", pickOrderId);
            payload.put("rfid_id", tagInfo.getEPC());
            payload.put("rssi", tagInfo.getRssi());
            payload.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));

            awsIotManager.publish(topic, payload.toString(), new AwsIotManager.PublishCallback() {
                @Override
                public void onPublishSuccess() {
                    Log.i(TAG, "Successfully published to " + topic);
                }

                @Override
                public void onPublishFailure(Exception exception) {
                    Log.e(TAG, "Failed to publish to " + topic, exception);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create JSON payload", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isScanning) {
            stopScanning();
        }
        if (uhfReader != null) {
            uhfReader.free();
        }
        if (awsIotManager != null) {
            awsIotManager.disconnect();
        }
    }
} 