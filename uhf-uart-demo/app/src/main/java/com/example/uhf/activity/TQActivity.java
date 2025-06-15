package com.example.uhf.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.adapter.TQTagAdapter;
import com.example.uhf.iot.AwsIotManager;
import com.example.uhf.tools.CheckUtils;
import com.example.uhf.tools.NumberTool;
import com.example.uhf.tools.StringUtils;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TQ 작업 액티비티
 * Package ID 입력 → RFID 스캔 → AWS IoT 이벤트 발송
 */
public class TQActivity extends AppCompatActivity {
    private static final String TAG = "TQActivity";
    
    // UI 요소들
    private Button btnBack, btnConfirmPackage, btnChangePackage;
    private Button btnSingleScan, btnLoopScan, btnClearTags;
    private EditText etPackageId;
    private TextView tvCurrentPackageId, tvScanCount, tvTotalCount, tvScanTime;
    private ListView lvScannedTags;
    private LinearLayout layoutPackageInput, layoutRfidScan;
    
    // 데이터 및 상태
    private String currentPackageId = "";
    private List<UHFTAGInfo> tagList;
    private TQTagAdapter tagAdapter;
    private boolean isScanning = false;
    private long startTime;
    private int totalScanCount = 0;
    
    // RFID 및 AWS IoT
    private RFIDWithUHFUART uhfReader;
    private AwsIotManager awsIotManager;
    
    // Handler Messages
    private static final int MSG_TAG_SCANNED = 1;
    private static final int MSG_UPDATE_TIME = 2;
    private static final int MSG_STOP_SCAN = 3;

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TAG_SCANNED:
                    UHFTAGInfo tagInfo = (UHFTAGInfo) msg.obj;
                    handleTagScanned(tagInfo);
                    break;
                case MSG_UPDATE_TIME:
                    if (isScanning) {
                        updateScanTime();
                        handler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, 100);
                    }
                    break;
                case MSG_STOP_SCAN:
                    stopScanning();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tq);
        
        initializeViews();
        initializeData();
        initializeRFID();
        initializeAwsIot();
        setupClickListeners();
    }
    
    private void initializeViews() {
        // 버튼들
        btnBack = findViewById(R.id.btnBack);
        btnConfirmPackage = findViewById(R.id.btnConfirmPackage);
        btnChangePackage = findViewById(R.id.btnChangePackage);
        btnSingleScan = findViewById(R.id.btnSingleScan);
        btnLoopScan = findViewById(R.id.btnLoopScan);
        btnClearTags = findViewById(R.id.btnClearTags);
        
        // 입력 및 표시 요소들
        etPackageId = findViewById(R.id.etPackageId);
        tvCurrentPackageId = findViewById(R.id.tvCurrentPackageId);
        tvScanCount = findViewById(R.id.tvScanCount);
        tvTotalCount = findViewById(R.id.tvTotalCount);
        tvScanTime = findViewById(R.id.tvScanTime);
        lvScannedTags = findViewById(R.id.lvScannedTags);
        
        // 레이아웃 컨테이너들
        layoutPackageInput = findViewById(R.id.layoutPackageInput);
        layoutRfidScan = findViewById(R.id.layoutRfidScan);
    }
    
    private void initializeData() {
        tagList = new ArrayList<>();
        tagAdapter = new TQTagAdapter(this, tagList);
        lvScannedTags.setAdapter(tagAdapter);
        
        updateCounters();
    }
    
    private void initializeRFID() {
        // RFID 리더 직접 초기화
        try {
            uhfReader = com.rscja.deviceapi.RFIDWithUHFUART.getInstance();
            if (uhfReader != null) {
                if (uhfReader.init(this)) {  // Context 파라미터 추가
                    Log.i(TAG, "RFID 리더 초기화 성공");
                } else {
                    Log.e(TAG, "RFID 리더 초기화 실패");
                    Toast.makeText(this, "RFID 리더 초기화에 실패했습니다.", Toast.LENGTH_LONG).show();
                }
            } else {
                Log.e(TAG, "RFID 리더 인스턴스를 가져올 수 없음");
                Toast.makeText(this, "RFID 리더를 초기화할 수 없습니다.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "RFID 초기화 오류: " + e.getMessage());
            Toast.makeText(this, "RFID 초기화 오류: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeAwsIot() {
        awsIotManager = new AwsIotManager(this);
        awsIotManager.connectWithCertificate(new AwsIotManager.ConnectionCallback() {
            @Override
            public void onConnectionSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(TQActivity.this, "AWS IoT 연결 성공", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onConnectionFailure(Exception exception) {
                runOnUiThread(() -> {
                    Toast.makeText(TQActivity.this, "AWS IoT 연결 실패", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void setupClickListeners() {
        // 뒤로가기 버튼
        btnBack.setOnClickListener(v -> finish());
        
        // Package ID 확인 버튼
        btnConfirmPackage.setOnClickListener(v -> confirmPackageId());
        
        // Package ID 변경 버튼
        btnChangePackage.setOnClickListener(v -> changePackageId());
        
        // 단일 스캔 버튼
        btnSingleScan.setOnClickListener(v -> performSingleScan());
        
        // 연속 스캔 버튼
        btnLoopScan.setOnClickListener(v -> toggleLoopScan());
        
        // 목록 지우기 버튼
        btnClearTags.setOnClickListener(v -> clearTagList());
    }
    
    private void confirmPackageId() {
        String packageId = etPackageId.getText().toString().trim();
        
        if (TextUtils.isEmpty(packageId)) {
            Toast.makeText(this, "Package ID를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentPackageId = packageId;
        tvCurrentPackageId.setText(currentPackageId);
        
        // Package ID 입력 화면 숨기고 RFID 스캔 화면 표시
        layoutPackageInput.setVisibility(View.GONE);
        layoutRfidScan.setVisibility(View.VISIBLE);
        
        Toast.makeText(this, "Package ID 설정 완료: " + currentPackageId, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Package ID 설정: " + currentPackageId);
    }
    
    private void changePackageId() {
        // RFID 스캔 화면 숨기고 Package ID 입력 화면 표시
        layoutRfidScan.setVisibility(View.GONE);
        layoutPackageInput.setVisibility(View.VISIBLE);
        
        // 기존 Package ID를 입력 필드에 표시
        etPackageId.setText(currentPackageId);
        etPackageId.selectAll();
        
        // 스캔 중이면 중지
        if (isScanning) {
            stopScanning();
        }
    }
    
    private void performSingleScan() {
        if (TextUtils.isEmpty(currentPackageId)) {
            Toast.makeText(this, "먼저 Package ID를 설정해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.i(TAG, "단일 스캔 시작");
        
        UHFTAGInfo tagInfo = uhfReader.inventorySingleTag();
        if (tagInfo != null) {
            handleTagScanned(tagInfo);
            publishToAwsIot(tagInfo);
            Toast.makeText(this, "태그 스캔 성공", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "태그를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void toggleLoopScan() {
        if (TextUtils.isEmpty(currentPackageId)) {
            Toast.makeText(this, "먼저 Package ID를 설정해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isScanning) {
            stopScanning();
        } else {
            startLoopScanning();
        }
    }
    
    private void startLoopScanning() {
        Log.i(TAG, "연속 스캔 시작");
        
        uhfReader.setInventoryCallback(new IUHFInventoryCallback() {
            @Override
            public void callback(UHFTAGInfo uhftagInfo) {
                Message msg = handler.obtainMessage();
                msg.obj = uhftagInfo;
                msg.what = MSG_TAG_SCANNED;
                handler.sendMessage(msg);
            }
        });
        
        InventoryParameter inventoryParameter = new InventoryParameter();
        if (uhfReader.startInventoryTag(inventoryParameter)) {
            isScanning = true;
            startTime = SystemClock.elapsedRealtime();
            
            btnLoopScan.setText("스캔 중지");
            btnLoopScan.setBackgroundResource(R.drawable.btn_clear_background);
            btnSingleScan.setEnabled(false);
            
            handler.sendEmptyMessage(MSG_UPDATE_TIME);
            
            Toast.makeText(this, "연속 스캔을 시작했습니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "연속 스캔을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopScanning() {
        if (!isScanning) return;
        
        Log.i(TAG, "연속 스캔 중지");
        
        isScanning = false;
        uhfReader.stopInventory();
        
        btnLoopScan.setText("연속 스캔");
        btnLoopScan.setBackgroundResource(R.drawable.btn_loop_scan_background);
        btnSingleScan.setEnabled(true);
        
        handler.removeMessages(MSG_UPDATE_TIME);
        
        Toast.makeText(this, "연속 스캔을 중지했습니다.", Toast.LENGTH_SHORT).show();
    }
    
    private void handleTagScanned(UHFTAGInfo tagInfo) {
        String epc = tagInfo.getEPC();
        if (StringUtils.isNotEmpty(epc)) {
            boolean[] exists = new boolean[1];
            int insertIndex = CheckUtils.getInsertIndex(tagList, tagInfo, exists);
            
            if (exists[0]) {
                // 기존 태그의 카운트 증가
                tagInfo.setCount(tagList.get(insertIndex).getCount() + 1);
                tagList.set(insertIndex, tagInfo);
            } else {
                // 새로운 태그 추가
                tagList.add(insertIndex, tagInfo);
            }
            
            totalScanCount++;
            updateCounters();
            tagAdapter.notifyDataSetChanged();
            
            // AWS IoT로 이벤트 발송 (연속 스캔 모드에서)
            if (isScanning) {
                publishToAwsIot(tagInfo);
            }
            
            Log.i(TAG, "태그 스캔됨: " + epc + ", RSSI: " + tagInfo.getRssi());
        }
    }
    
    private void publishToAwsIot(UHFTAGInfo tagInfo) {
        if (awsIotManager == null || !awsIotManager.isConnected()) {
            Log.w(TAG, "AWS IoT가 연결되지 않아 이벤트를 발송할 수 없습니다.");
            return;
        }
        
        // 현재 시간 생성
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        String timestamp = sdf.format(new Date());
        
        // AWS IoT 이벤트 발송 (실제 Package ID 사용)
        awsIotManager.publishRfidScanEvent(
            tagInfo.getEPC(),
            tagInfo.getRssi(),
            timestamp,
            currentPackageId,  // 실제 Package ID 전달
            new AwsIotManager.PublishCallback() {
                @Override
                public void onPublishSuccess() {
                    Log.i(TAG, "✅ TQ 이벤트 발송 성공: " + tagInfo.getEPC() + " -> Package: " + currentPackageId);
                }

                @Override
                public void onPublishFailure(Exception exception) {
                    Log.e(TAG, "❌ TQ 이벤트 발송 실패: " + exception.getMessage());
                }
            }
        );
    }
    
    private void updateScanTime() {
        if (isScanning) {
            float useTime = (SystemClock.elapsedRealtime() - startTime) / 1000.0F;
            tvScanTime.setText(NumberTool.getPointDouble(1, useTime) + "s");
        }
    }
    
    private void updateCounters() {
        tvScanCount.setText(String.valueOf(tagList.size()));
        tvTotalCount.setText(String.valueOf(totalScanCount));
    }
    
    private void clearTagList() {
        tagList.clear();
        totalScanCount = 0;
        updateCounters();
        tagAdapter.notifyDataSetChanged();
        tvScanTime.setText("0s");
        
        Toast.makeText(this, "목록을 지웠습니다.", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (isScanning) {
            stopScanning();
        }
        
        if (awsIotManager != null) {
            awsIotManager.disconnect();
        }
        
        if (uhfReader != null) {
            uhfReader.setInventoryCallback(null);
            // RFID 리더 리소스 해제 (필요시)
            try {
                uhfReader.free();
                Log.i(TAG, "RFID 리더 리소스 해제 완료");
            } catch (Exception e) {
                Log.e(TAG, "RFID 리더 해제 오류: " + e.getMessage());
            }
        }
    }
}