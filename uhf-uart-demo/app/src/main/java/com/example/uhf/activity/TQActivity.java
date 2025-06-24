package com.example.uhf.activity;

import android.content.Intent;
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

// 바코드 스캔 라이브러리 추가
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

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
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

/**
 * TQ 작업 액티비티
 * Package ID 입력 → RFID 스캔 → AWS IoT 이벤트 발송
 */
public class TQActivity extends AppCompatActivity {
    private static final String TAG = "TQActivity";
    
    // User info from intent
    private String apiKey;
    private String userRole;
    private String employeeId;
    
    // UI 요소들
    private Button btnBack, btnConfirmPackage;
    private Button btnScanBarcode; // 바코드 스캔 버튼
    private Button btnLoopScan, btnTqComplete;
    private EditText etPackageId;
    private TextView tvCurrentPackageId, tvUserInfo; // 사용자 정보 표시
    private LinearLayout layoutPackageInput, layoutRfidScan;
    
    // 패키지 정보 관련 뷰
    private LinearLayout layoutPackageInfo;
    private TextView tvPackageId, tvProductId, tvStatus, tvQuantity, tvTqScannedQuantity, tvWeight, tvSize, tvBreadth, tvHeight, tvWidth, tvStoringOrderId;
    private Button btnQualityCheck, btnQualityCheckFail;
    private EditText etQualityFailDesc;
    private TextView tvQuantityInfo, tvScannedCount;
    
    // 데이터 및 상태
    private String currentPackageId = "";
    private List<UHFTAGInfo> tagList;
    private boolean isScanning = false;
    
    // RFID 및 AWS IoT
    private RFIDWithUHFUART uhfReader;
    private AwsIotManager awsIotManager;
    
    // Handler Messages
    private static final int MSG_TAG_SCANNED = 1;
    private static final int MSG_UPDATE_TIME = 2;
    private static final int MSG_STOP_SCAN = 3;

    // 패키지 정보 관련 상태 변수
    private String productId = "";
    private String packageStatus = "";
    private int packageQuantity = 0;
    private int tqScannedQuantity = 0;
    private String weight = "";
    private String size = "";
    private String breadth = "";
    private String height = "";
    private String width = "";
    private String storingOrderId = "";

    private Button btnRfidFail;

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
        
        // Get user info from intent
        Intent intent = getIntent();
        apiKey = intent.getStringExtra("api_key");
        userRole = intent.getStringExtra("role");
        employeeId = intent.getStringExtra("employee_id");
        
        initializeViews();
        initializeData();
        initializeRFID();
        initializeAwsIot();
        setupClickListeners();
        
        // Display user information
        updateUserInfo();
    }
    
    private void initializeViews() {
        // 버튼들
        btnBack = findViewById(R.id.btnBack);
        btnConfirmPackage = findViewById(R.id.btnConfirmPackage);
        btnScanBarcode = findViewById(R.id.btnScanBarcode); // 바코드 스캔 버튼 초기화
        btnLoopScan = findViewById(R.id.btnLoopScan);
        btnTqComplete = findViewById(R.id.btnTqComplete);
        // 입력 및 표시 요소들
        etPackageId = findViewById(R.id.etPackageId);
        tvCurrentPackageId = findViewById(R.id.tvCurrentPackageId);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        // 레이아웃 컨테이너들
        layoutPackageInput = findViewById(R.id.layoutPackageInput);
        layoutRfidScan = findViewById(R.id.layoutRfidScan);
        // 패키지 정보 관련 뷰
        layoutPackageInfo = findViewById(R.id.layoutPackageInfo);
        tvPackageId = findViewById(R.id.tvPackageId);
        tvProductId = findViewById(R.id.tvProductId);
        tvStatus = findViewById(R.id.tvStatus);
        tvQuantity = findViewById(R.id.tvQuantity);
        tvTqScannedQuantity = findViewById(R.id.tvTqScannedQuantity);
        tvWeight = findViewById(R.id.tvWeight);
        tvSize = findViewById(R.id.tvSize);
        tvBreadth = findViewById(R.id.tvBreadth);
        tvHeight = findViewById(R.id.tvHeight);
        tvWidth = findViewById(R.id.tvWidth);
        tvStoringOrderId = findViewById(R.id.tvStoringOrderId);
        btnQualityCheck = findViewById(R.id.btnQualityCheck);
        btnQualityCheckFail = findViewById(R.id.btnQualityCheckFail);
        etQualityFailDesc = findViewById(R.id.etQualityFailDesc);
        tvQuantityInfo = findViewById(R.id.tvQuantityInfo);
        tvScannedCount = findViewById(R.id.tvScannedCount);
        btnRfidFail = findViewById(R.id.btnRfidFail);
    }
    
    private void updateUserInfo() {
        if (!TextUtils.isEmpty(userRole) && !TextUtils.isEmpty(employeeId)) {
            String userInfoText = "🧑‍💼 " + userRole + " | ID: " + employeeId;
            tvUserInfo.setText(userInfoText);
            tvUserInfo.setVisibility(View.VISIBLE);
        } else {
            tvUserInfo.setVisibility(View.GONE);
        }
    }
    
    private void initializeData() {
        tagList = new ArrayList<>();
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
                    Toast.makeText(this, getString(R.string.rfid_reader_init_failed), Toast.LENGTH_LONG).show();
                }
            } else {
                Log.e(TAG, "RFID 리더 인스턴스를 가져올 수 없음");
                Toast.makeText(this, getString(R.string.rfid_reader_init_failed), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "RFID 초기화 오류: " + e.getMessage());
            Toast.makeText(this, getString(R.string.rfid_reader_init_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeAwsIot() {
        awsIotManager = new AwsIotManager(this);
        awsIotManager.connectWithCertificate(new AwsIotManager.ConnectionCallback() {
            @Override
            public void onConnectionSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(TQActivity.this, getString(R.string.aws_iot_connection_success), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onConnectionFailure(Exception exception) {
                runOnUiThread(() -> {
                    Toast.makeText(TQActivity.this, getString(R.string.aws_iot_connection_failed), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void setupClickListeners() {
        // 뒤로가기 버튼
        btnBack.setOnClickListener(v -> finish());
        // Package ID 확인 버튼
        btnConfirmPackage.setOnClickListener(v -> confirmPackageId());
        // 바코드 스캔 버튼
        btnScanBarcode.setOnClickListener(v -> startBarcodeScanning());
        // Scan 버튼(연속 스캔)
        btnLoopScan.setOnClickListener(v -> toggleLoopScan());
        // 퀄리티 체크 버튼
        btnQualityCheck.setOnClickListener(v -> {
            // 품질 체크 PASS
            sendQualityCheck("pass", null);
        });
        btnQualityCheckFail.setOnClickListener(v -> {
            // 품질 체크 FAIL
            String desc = etQualityFailDesc.getText().toString().trim();
            if (desc.isEmpty()) {
                Toast.makeText(this, "Please enter fail reason.", Toast.LENGTH_SHORT).show();
                return;
            }
            sendQualityCheck("fail", desc);
        });
        btnRfidFail.setOnClickListener(v -> {
            // RFID FAIL (close-tq API)
            sendRfidFail();
        });
        btnTqComplete.setOnClickListener(v -> {
            // TQ Complete (PASS)
            sendTqComplete();
        });
    }
    
    private void confirmPackageId() {
        String packageId = etPackageId.getText().toString().trim();
        if (TextUtils.isEmpty(packageId)) {
            Toast.makeText(this, getString(R.string.enter_package_id), Toast.LENGTH_SHORT).show();
            return;
        }
        currentPackageId = packageId;
        fetchPackageInfo(packageId);
    }
    
    private void fetchPackageInfo(String packageId) {
        layoutPackageInput.setVisibility(View.GONE);
        layoutPackageInfo.setVisibility(View.VISIBLE);
        layoutRfidScan.setVisibility(View.GONE);
        // start-tq API POST 요청
        String url = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/packages/" + packageId + "/start-tq";
        OkHttpClient client = new OkHttpClient();
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("employee_id", employeeId);
            body.put("role", userRole);
            okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(body.toString(), okhttp3.MediaType.get("application/json; charset=utf-8"));
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .post(reqBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(TQActivity.this, "Failed to fetch package info: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        layoutPackageInput.setVisibility(View.VISIBLE);
                        layoutPackageInfo.setVisibility(View.GONE);
                    });
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    String body = response.body().string();
                    try {
                        JSONObject obj = new JSONObject(body);
                        JSONObject data = obj.optJSONObject("data");
                        if (data != null) {
                            productId = data.optString("product_id", "");
                            packageStatus = data.optString("status", "");
                            packageQuantity = data.optInt("quantity", 0);
                            tqScannedQuantity = data.optInt("tq_scanned_quantity", 0);
                            weight = data.optString("weight", "");
                            size = data.optString("package_size_category", "");
                            breadth = data.optString("breadth", "");
                            height = data.optString("height", "");
                            width = data.optString("width", "");
                            storingOrderId = data.optString("storing_order_id", "");
                            runOnUiThread(() -> {
                                tvPackageId.setText("Package ID: " + packageId);
                                tvProductId.setText("Product ID: " + productId);
                                tvStatus.setText("Status: " + packageStatus);
                                tvQuantity.setText("Quantity: " + packageQuantity);
                                tvTqScannedQuantity.setText("TQ Scanned: " + tqScannedQuantity);
                                tvWeight.setText("Weight: " + weight);
                                tvSize.setText("Size: " + size);
                                tvBreadth.setText("Breadth: " + breadth);
                                tvHeight.setText("Height: " + height);
                                tvWidth.setText("Width: " + width);
                                tvStoringOrderId.setText("Storing Order ID: " + storingOrderId);
                                btnQualityCheck.setVisibility(View.VISIBLE);
                                // RFID Scan Section에도 표시
                                tvQuantityInfo.setText("Quantity: " + packageQuantity);
                                tvScannedCount.setText("Scanned: 0");
                                btnTqComplete.setEnabled(false);
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(TQActivity.this, "No data in response", Toast.LENGTH_LONG).show();
                                layoutPackageInput.setVisibility(View.VISIBLE);
                                layoutPackageInfo.setVisibility(View.GONE);
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(TQActivity.this, "Failed to parse package info", Toast.LENGTH_LONG).show();
                            layoutPackageInput.setVisibility(View.VISIBLE);
                            layoutPackageInfo.setVisibility(View.GONE);
                        });
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(TQActivity.this, "Failed to build package info request", Toast.LENGTH_LONG).show();
            layoutPackageInput.setVisibility(View.VISIBLE);
            layoutPackageInfo.setVisibility(View.GONE);
        }
    }
    
    /**
     * 바코드 스캔 시작
     */
    private void startBarcodeScanning() {
        Log.i(TAG, "바코드 스캔 시작");
        
        try {
            IntentIntegrator integrator = new IntentIntegrator(this);
            
            // Set barcode formats (support all formats like QR, Code128, Code39)
            integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
            
            // Set scan screen
            integrator.setPrompt(getString(R.string.barcode_scan_prompt));
            integrator.setCameraId(0); // 후면 카메라 사용
            integrator.setBeepEnabled(true); // 스캔 성공시 비프음
            integrator.setBarcodeImageEnabled(false); // 이미지 저장 비활성화
            
            // 세로 방향으로 고정 (90도 회전된 상태)
            integrator.setOrientationLocked(true); // 방향 고정 활성화
            
            // 세로 방향 강제 설정을 위한 추가 옵션
            integrator.addExtra("SCAN_ORIENTATION_LOCKED", true);
            integrator.addExtra("ORIENTATION_LOCK", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            
            // 사용자 정의 카메라 액티비티 사용 (필요시)
            integrator.setCaptureActivity(CustomCaptureActivity.class);
            
            // 스캔 시작
            integrator.initiateScan();
            
            Toast.makeText(this, getString(R.string.barcode_scan_starting), Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e(TAG, "바코드 스캔 시작 오류: " + e.getMessage());
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show();
        }
    }
    
    private void toggleLoopScan() {
        if (TextUtils.isEmpty(currentPackageId)) {
            Toast.makeText(this, "Please set Package ID first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isScanning) {
            stopScanning();
        } else {
            startLoopScanning();
        }
    }
    
    private void startLoopScanning() {
        Log.i(TAG, "Start scanning");
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
            btnLoopScan.setText("Stop");
            Toast.makeText(this, "Scan started", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Cannot start scan", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopScanning() {
        if (!isScanning) return;
        Log.i(TAG, "Stop scanning");
        isScanning = false;
        uhfReader.stopInventory();
        btnLoopScan.setText("Scan");
        Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show();
    }
    
    private void handleTagScanned(UHFTAGInfo tagInfo) {
        String epc = tagInfo.getEPC();
        // 이미 기대 수량만큼 스캔되었으면 더 이상 추가하지 않음
        if (tagList.size() >= packageQuantity) {
            return;
        }
        if (StringUtils.isNotEmpty(epc)) {
            boolean[] exists = new boolean[1];
            int insertIndex = CheckUtils.getInsertIndex(tagList, tagInfo, exists);
            if (exists[0]) {
                tagInfo.setCount(tagList.get(insertIndex).getCount() + 1);
                tagList.set(insertIndex, tagInfo);
            } else {
                tagList.add(insertIndex, tagInfo);
            }
            // UI에 유니크 개수 표시
            tvScannedCount.setText("Scanned: " + tagList.size());
            // 스캔 개수 == quantity면 TQ Complete 버튼 활성화
            if (tagList.size() == packageQuantity) {
                btnTqComplete.setEnabled(true);
            } else {
                btnTqComplete.setEnabled(false);
            }
            // AWS IoT로 이벤트 발송 (연속 스캔 모드에서)
            if (isScanning) {
                publishToAwsIot(tagInfo);
            }
            Log.i(TAG, "Tag scanned: " + epc + ", RSSI: " + tagInfo.getRssi());
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
    
    private void sendQualityCheck(String flag, String desc) {
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("package_id", currentPackageId);
            body.put("employee_id", employeeId);
            body.put("role", userRole);
            body.put("flag", flag);
            if (flag.equals("fail") && desc != null) {
                body.put("description", desc);
            }
            RequestBody reqBody = RequestBody.create(body.toString(), okhttp3.MediaType.get("application/json; charset=utf-8"));
            Request req = new Request.Builder()
                    .url("https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/tq-quality-check")
                    .post(reqBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            OkHttpClient client = new OkHttpClient();
            client.newCall(req).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(TQActivity.this, "Quality check failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    runOnUiThread(() -> {
                        if (flag.equals("pass")) {
                            layoutPackageInfo.setVisibility(View.GONE);
                            layoutRfidScan.setVisibility(View.VISIBLE);
                            btnTqComplete.setEnabled(false);
                            tagList.clear();
                            tvScannedCount.setText("Scanned: 0");
                            Toast.makeText(TQActivity.this, "Quality check passed.", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(TQActivity.this, "Quality check failed and reported.", Toast.LENGTH_LONG).show();
                            finish();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(TQActivity.this, "Quality check request error", Toast.LENGTH_LONG).show();
        }
    }
    private void sendRfidFail() {
        String url = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/packages/" + currentPackageId + "/close-tq";
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("employee_id", employeeId);
            body.put("role", userRole);
            body.put("flag", "fail");
            okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(body.toString(), okhttp3.MediaType.get("application/json; charset=utf-8"));
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(url)
                    .post(reqBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            client.newCall(req).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(TQActivity.this, "RFID Fail report failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    runOnUiThread(() -> {
                        Toast.makeText(TQActivity.this, "RFID Fail reported.", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(TQActivity.this, "RFID Fail request error", Toast.LENGTH_LONG).show();
        }
    }
    private void sendTqComplete() {
        String url = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/packages/" + currentPackageId + "/close-tq";
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("employee_id", employeeId);
            body.put("role", userRole);
            okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(body.toString(), okhttp3.MediaType.get("application/json; charset=utf-8"));
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(url)
                    .post(reqBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            client.newCall(req).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(TQActivity.this, "TQ Complete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    runOnUiThread(() -> {
                        Toast.makeText(TQActivity.this, "TQ Complete!", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(TQActivity.this, "TQ Complete request error", Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * 바코드 스캔 결과 처리
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        
        if (result != null) {
            if (result.getContents() != null) {
                // 바코드 스캔 성공
                String scannedCode = result.getContents().trim();
                
                Log.i(TAG, "바코드 스캔 성공: " + scannedCode);
                
                // Package ID 입력 필드에 스캔 결과 설정
                etPackageId.setText(scannedCode);
                
                Toast.makeText(this, getString(R.string.barcode_scan_complete, scannedCode), Toast.LENGTH_SHORT).show();
                
                // 자동으로 Package ID 확인 (사용자 편의성 향상)
                // 2초 후 자동 확인 (사용자가 스캔 결과를 확인할 시간을 주기 위해)
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // EditText에 값이 있으면 자동 확인
                        if (!TextUtils.isEmpty(etPackageId.getText().toString().trim())) {
                            confirmPackageId();
                        }
                    }
                }, 2000); // 2초 후 자동 확인
                
            } else {
                // 바코드 스캔 취소 또는 실패
                Log.i(TAG, "바코드 스캔이 취소되었습니다.");
                Toast.makeText(this, getString(R.string.barcode_scan_cancelled), Toast.LENGTH_SHORT).show();
            }
        }
        
        super.onActivityResult(requestCode, resultCode, data);
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