package com.example.uhf.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.iot.AwsIotManager;
import com.example.uhf.api.ApiClient;
import com.example.uhf.util.ApiTestUtil;

/**
 * TQ System Main Activity
 * API Key Input → TQ, BIN, PICK, PACK, DISPATCH feature selection
 */
public class TQMainActivity extends AppCompatActivity {
    private static final String TAG = "TQMainActivity";
    private static final String PREFS_NAME = "TQSystemPrefs";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_ROLE = "role";
    private static final String KEY_EMPLOYEE_ID = "employee_id";
    
    // API Key input screen
    private LinearLayout layoutApiKey;
    private EditText etApiKey;
    private Button btnScanApiKey;
    private Button btnConfirmApiKey;
    private ProgressBar progressApiKey;
    
    // Main feature screen
    private LinearLayout layoutMainButtons;
    private Button btnTQ, btnBIN, btnPICK, btnPACK, btnDISPATCH;
    private Button btnChangeApiKey;
    private Button btnSampleTQ, btnSampleBIN, btnSamplePICK1, btnSamplePICK2, btnSamplePACK, btnSampleDISP;
    
    // Status display
    private TextView tvIotStatus;
    private TextView tvUserInfo;
    
    // AWS IoT management
    private AwsIotManager awsIotManager;
    private String currentApiKey = "";
    private String userRole = "";
    private String employeeId = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        loadSavedData();
        setupClickListeners();
        
        // If saved API Key exists, go to main screen; otherwise, show API Key input screen
        if (!TextUtils.isEmpty(currentApiKey) && !TextUtils.isEmpty(userRole)) {
            showMainButtons();
            updateUserInfo();
            initializeAwsIot();
        } else {
            showApiKeyInput();
        }
    }
    
    private void initializeViews() {
        // API Key input screen
        layoutApiKey = findViewById(R.id.layoutApiKey);
        etApiKey = findViewById(R.id.etApiKey);
        btnScanApiKey = findViewById(R.id.btnScanApiKey);
        btnConfirmApiKey = findViewById(R.id.btnConfirmApiKey);
        progressApiKey = findViewById(R.id.progressApiKey);
        
        // Main feature screen
        layoutMainButtons = findViewById(R.id.layoutMainButtons);
        btnTQ = findViewById(R.id.btnTQ);
        btnBIN = findViewById(R.id.btnBIN);
        btnPICK = findViewById(R.id.btnPICK);
        btnPACK = findViewById(R.id.btnPACK);
        btnDISPATCH = findViewById(R.id.btnDISPATCH);
        btnChangeApiKey = findViewById(R.id.btnChangeApiKey);
        btnSampleTQ = findViewById(R.id.btnSampleTQ);
        btnSampleBIN = findViewById(R.id.btnSampleBIN);
        btnSamplePICK1 = findViewById(R.id.btnSamplePICK1);
        btnSamplePICK2 = findViewById(R.id.btnSamplePICK2);
        btnSamplePACK = findViewById(R.id.btnSamplePACK);
        btnSampleDISP = findViewById(R.id.btnSampleDISP);
        
        // Status display
        tvIotStatus = findViewById(R.id.tvIotStatus);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        
        // Initialize progress bar as invisible
        if (progressApiKey != null) {
            progressApiKey.setVisibility(View.GONE);
        }
    }
    
    private void loadSavedData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentApiKey = prefs.getString(KEY_API_KEY, "");
        userRole = prefs.getString(KEY_ROLE, "");
        employeeId = prefs.getString(KEY_EMPLOYEE_ID, "");
        
        if (!TextUtils.isEmpty(currentApiKey)) {
            etApiKey.setText(currentApiKey);
        }
    }
    
    private void saveUserData(String apiKey, String role, String empId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_API_KEY, apiKey);
        editor.putString(KEY_ROLE, role);
        editor.putString(KEY_EMPLOYEE_ID, empId);
        editor.apply();
        
        currentApiKey = apiKey;
        userRole = role;
        employeeId = empId;
    }
    
    private void clearSavedData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        
        currentApiKey = "";
        userRole = "";
        employeeId = "";
    }
    
    private void showApiKeyInput() {
        layoutApiKey.setVisibility(View.VISIBLE);
        layoutMainButtons.setVisibility(View.GONE);
        updateIotStatus(getString(R.string.api_key_required), Color.parseColor("#e74c3c"));
    }
    
    private void showMainButtons() {
        layoutApiKey.setVisibility(View.GONE);
        layoutMainButtons.setVisibility(View.VISIBLE);
    }
    
    private void updateUserInfo() {
        if (!TextUtils.isEmpty(userRole) && !TextUtils.isEmpty(employeeId)) {
            String userInfoText = "Role: " + userRole + " | ID: " + employeeId;
            tvUserInfo.setText(userInfoText);
            tvUserInfo.setVisibility(View.VISIBLE);
        } else {
            tvUserInfo.setVisibility(View.GONE);
        }
    }
    
    private void setApiKeyInputEnabled(boolean enabled) {
        etApiKey.setEnabled(enabled);
        btnConfirmApiKey.setEnabled(enabled);
        
        if (progressApiKey != null) {
            progressApiKey.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
    }
    
    private void initializeAwsIot() {
        // AWS IoT connection initialization
        awsIotManager = new AwsIotManager(this);
        updateIotStatus(getString(R.string.iot_connecting), Color.parseColor("#f39c12"));
        
        awsIotManager.connectWithCertificate(new AwsIotManager.ConnectionCallback() {
            @Override
            public void onConnectionSuccess() {
                runOnUiThread(() -> {
                    updateIotStatus(getString(R.string.iot_connected), Color.parseColor("#27ae60"));
                    Toast.makeText(TQMainActivity.this, getString(R.string.aws_iot_connection_success), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onConnectionFailure(Exception exception) {
                runOnUiThread(() -> {
                    updateIotStatus(getString(R.string.iot_failed), Color.parseColor("#e74c3c"));
                    Toast.makeText(TQMainActivity.this, getString(R.string.aws_iot_connection_failed) + ": " + exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void updateIotStatus(String status, int color) {
        tvIotStatus.setText(status);
        tvIotStatus.setTextColor(color);
    }
    
    private void setupClickListeners() {
        // API Key confirm button
        btnConfirmApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmApiKey();
            }
        });
        
        // API Key change button
        btnChangeApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeApiKey();
            }
        });
        
        // TQ button click - go to TQ activity
        btnTQ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityWithUserData(TQActivity.class, "TQ");
            }
        });
        
        // BIN button click
        btnBIN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TQMainActivity.this, BinningActivity.class);
                intent.putExtra("api_key", currentApiKey);
                intent.putExtra("role", userRole);
                intent.putExtra("employee_id", employeeId);
                startActivity(intent);
            }
        });
        
        // PICK button click
        btnPICK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TQMainActivity.this, PickActivity.class);
                intent.putExtra("api_key", currentApiKey);
                intent.putExtra("role", userRole);
                intent.putExtra("employee_id", employeeId);
                startActivity(intent);
            }
        });
        
        // PACK button click
        btnPACK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TQMainActivity.this, PackingActivity.class);
                intent.putExtra("api_key", currentApiKey);
                intent.putExtra("role", userRole);
                intent.putExtra("employee_id", employeeId);
                startActivity(intent);
            }
        });
        
        // DISPATCH button click
        btnDISPATCH.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TQMainActivity.this, DispatchActivity.class);
                intent.putExtra("api_key", currentApiKey);
                intent.putExtra("role", userRole);
                intent.putExtra("employee_id", employeeId);
                startActivity(intent);
            }
        });
        
        // Go to existing UHF scanner (for debugging - long press PICK button)
        btnPICK.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(TQMainActivity.this, UHFMainActivity.class);
                startActivity(intent);
                return true;
            }
        });
        
        // API debugging - long press API Key confirm button
        btnConfirmApiKey.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String apiKey = etApiKey.getText().toString().trim();
                if (!TextUtils.isEmpty(apiKey)) {
                    Toast.makeText(TQMainActivity.this, "🔍 Running API connectivity tests...", Toast.LENGTH_SHORT).show();
                    ApiTestUtil.testApiConnectivity();
                    ApiTestUtil.testApiKeyMethods(apiKey);
                } else {
                    Toast.makeText(TQMainActivity.this, "Enter API key first for testing", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        
        // API Key 바코드 스캔 버튼
        btnScanApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBarcodeScanning();
            }
        });
        
        btnSampleTQ.setOnClickListener(v -> etApiKey.setText("tq-4c9d8e2f"));
        btnSampleBIN.setOnClickListener(v -> etApiKey.setText("bin-010101"));
        btnSamplePICK1.setOnClickListener(v -> etApiKey.setText("pic-999999"));
        btnSamplePICK2.setOnClickListener(v -> etApiKey.setText("pic-222222"));
        btnSamplePACK.setOnClickListener(v -> etApiKey.setText("pack-123123"));
        btnSampleDISP.setOnClickListener(v -> etApiKey.setText("disp-999999"));
    }
    
    private void confirmApiKey() {
        String apiKey = etApiKey.getText().toString().trim();
        
        if (TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, getString(R.string.enter_api_key), Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (apiKey.length() < 10) {
            Toast.makeText(this, getString(R.string.valid_api_key), Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show validation in progress
        updateIotStatus("Validating API Key...", Color.parseColor("#f39c12"));
        
        // Disable input during API call
        setApiKeyInputEnabled(false);
        
        // Validate API Key with server
        ApiClient.validateApiKey(apiKey, new ApiClient.ApiKeyCallback() {
            @Override
            public void onSuccess(ApiClient.ApiKeyResponse response) {
                runOnUiThread(() -> {
                    // Re-enable input
                    setApiKeyInputEnabled(true);
                    
                    // Save user data
                    saveUserData(response.apiKey, response.role, response.employeeId);
                    
                    // Update UI
                    updateUserInfo();
                    
                    // Switch to main screen
                    showMainButtons();
                    
                    // Initialize AWS IoT
                    initializeAwsIot();
                    
                    // Show success message
                    String successMessage = "✅ Login Success (" + response.role + ")";
                    Toast.makeText(TQMainActivity.this, successMessage, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // Re-enable input
                    setApiKeyInputEnabled(true);
                    
                    // Update status
                    updateIotStatus("API Key validation failed", Color.parseColor("#e74c3c"));
                    
                    // Show detailed error message
                    String errorMessage;
                    if (error.contains("403")) {
                        errorMessage = "❌ Authentication failed: Invalid API key or insufficient permissions";
                    } else if (error.contains("404")) {
                        errorMessage = "❌ API endpoint not found. Please check server configuration.";
                    } else if (error.contains("Network")) {
                        errorMessage = "❌ Network error: Please check your internet connection";
                    } else {
                        errorMessage = "❌ API Key validation failed: " + error;
                    }
                    
                    Toast.makeText(TQMainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void changeApiKey() {
        // Disconnect AWS IoT
        if (awsIotManager != null) {
            awsIotManager.disconnect();
        }
        
        // Clear saved data
        clearSavedData();
        
        // Switch to API Key input screen
        showApiKeyInput();
        
        // Focus input field
        etApiKey.requestFocus();
        etApiKey.selectAll();
    }
    
    private void startActivityWithUserData(Class<?> activityClass, String activityName) {
        if (TextUtils.isEmpty(currentApiKey) || TextUtils.isEmpty(userRole)) {
            Toast.makeText(this, getString(R.string.set_api_key_first), Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent intent = new Intent(this, activityClass);
        intent.putExtra("api_key", currentApiKey);
        intent.putExtra("role", userRole);
        intent.putExtra("employee_id", employeeId);
        startActivity(intent);
    }
    
    private void showComingSoonMessage(String featureName) {
        Toast.makeText(this, getString(R.string.coming_soon, featureName), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (awsIotManager != null) {
            awsIotManager.disconnect();
        }
    }
    
    // 바코드 스캔 시작 (API Key용)
    private void startBarcodeScanning() {
        try {
            com.google.zxing.integration.android.IntentIntegrator integrator = new com.google.zxing.integration.android.IntentIntegrator(this);
            integrator.setDesiredBarcodeFormats(com.google.zxing.integration.android.IntentIntegrator.ALL_CODE_TYPES);
            integrator.setPrompt(getString(R.string.barcode_scan_prompt));
            integrator.setCameraId(0);
            integrator.setBeepEnabled(true);
            integrator.setBarcodeImageEnabled(false);
            integrator.setOrientationLocked(true);
            integrator.setCaptureActivity(CustomCaptureActivity.class);
            integrator.initiateScan();
            Toast.makeText(this, getString(R.string.barcode_scan_starting), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e(TAG, "바코드 스캔 시작 오류: " + e.getMessage());
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        com.google.zxing.integration.android.IntentResult result = com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                String scannedCode = result.getContents().trim();
                etApiKey.setText(scannedCode);
                Toast.makeText(this, getString(R.string.barcode_scan_complete, scannedCode), Toast.LENGTH_SHORT).show();
                // 1초 후 자동으로 API Key 확인
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!TextUtils.isEmpty(etApiKey.getText().toString().trim())) {
                            confirmApiKey();
                        }
                    }
                }, 1000);
            } else {
                Toast.makeText(this, getString(R.string.barcode_scan_cancelled), Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}