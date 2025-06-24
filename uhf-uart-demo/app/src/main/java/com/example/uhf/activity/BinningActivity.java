package com.example.uhf.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.tools.UIHelper;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BinningActivity extends AppCompatActivity {

    private static final String TAG = "BinningActivity";
    private static final String API_URL_BIN_ALLOCATION = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/bin-allocation";
    private static final String API_URL_CLOSE_BINNING = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/packages/%s/close-binning";
    private String API_KEY = "";
    private String userRole = "";
    private String employeeId = "";
    private String currentPackageId = "";

    // UHF
    private RFIDWithUHFUART mReader;
    private boolean isScanning = false;
    private String currentScanningBinId = null;
    private Map<String, Set<String>> scannedTagsPerBin = new HashMap<>();
    private Map<String, Integer> requiredQuantities = new HashMap<>();

    private final Handler uhfHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(android.os.Message msg) {
            if (msg.what == 1 && msg.obj != null) {
                UHFTAGInfo uhftagInfo = (UHFTAGInfo) msg.obj;
                if (isScanning && currentScanningBinId != null) {
                    handleEpc(uhftagInfo.getEPC());
                }
            }
        }
    };

    // Header and User Info
    private Button btnBack;
    private TextView tvUserInfo;

    // Package ID Input
    private LinearLayout layoutPackageInput;
    private EditText etPackageId;
    private Button btnScanPackageId;
    private Button btnConfirmPackage;

    // Bin Allocation List
    private ScrollView binAllocationContainer;
    private TextView tvCurrentPackageId;
    private LinearLayout binListContainer;
    private Button btnConfirmBinning;
    
    // Others
    private ProgressBar progressBar;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_binning);

        getIntentExtras();
        initializeViews();
        setupHeader();
        setupClickListeners();
        initUHF();
    }

    @Override
    protected void onDestroy() {
        if (mReader != null) {
            mReader.free();
        }
        super.onDestroy();
    }

    private void getIntentExtras() {
        API_KEY = getIntent().getStringExtra("api_key");
        userRole = getIntent().getStringExtra("role");
        employeeId = getIntent().getStringExtra("employee_id");
    }

    private void initializeViews() {
        btnBack = findViewById(R.id.btnBack);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        layoutPackageInput = findViewById(R.id.layoutPackageInput);
        etPackageId = findViewById(R.id.etPackageId);
        btnScanPackageId = findViewById(R.id.btnScanPackageId);
        btnConfirmPackage = findViewById(R.id.btnConfirmPackage);
        binAllocationContainer = findViewById(R.id.binAllocationContainer);
        tvCurrentPackageId = findViewById(R.id.tvCurrentPackageId);
        binListContainer = findViewById(R.id.binListContainer);
        btnConfirmBinning = findViewById(R.id.btnConfirmBinning);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupHeader() {
        btnBack.setOnClickListener(v -> finish());
        if (userRole != null && employeeId != null && !userRole.isEmpty() && !employeeId.isEmpty()) {
            String userInfoText = "🧑‍💼 " + userRole + " | ID: " + employeeId;
            tvUserInfo.setText(userInfoText);
            tvUserInfo.setVisibility(View.VISIBLE);
        } else {
            tvUserInfo.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        btnScanPackageId.setOnClickListener(v -> startBarcodeScanning());
        btnConfirmPackage.setOnClickListener(v -> {
            String packageId = etPackageId.getText().toString().trim();
            if (!TextUtils.isEmpty(packageId)) {
                fetchBinAllocation(packageId);
            } else {
                Toast.makeText(this, "Please enter a Package ID.", Toast.LENGTH_SHORT).show();
            }
        });
        btnConfirmBinning.setOnClickListener(v -> {
            Log.d(TAG, "btnConfirmBinning clicked. currentPackageId=" + currentPackageId);
            if (!TextUtils.isEmpty(currentPackageId)) {
                confirmBinningOnServer();
            } else {
                Toast.makeText(this, "Package ID not found.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startBarcodeScanning() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(CustomCaptureActivity.class);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("Scan Package ID");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show();
            } else {
                String packageId = result.getContents();
                etPackageId.setText(packageId);
                fetchBinAllocation(packageId);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void initUHF() {
        try {
            mReader = RFIDWithUHFUART.getInstance();
            mReader.init(this);
            mReader.setInventoryCallback(new IUHFInventoryCallback() {
                @Override
                public void callback(UHFTAGInfo uhftagInfo) {
                    android.os.Message msg = uhfHandler.obtainMessage(1, uhftagInfo);
                    uhfHandler.sendMessage(msg);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize UHF reader", e);
            Toast.makeText(this, "UHF Reader Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleEpc(String epc) {
        if (currentScanningBinId == null || !scannedTagsPerBin.containsKey(currentScanningBinId)) return;
        Set<String> scannedTags = scannedTagsPerBin.get(currentScanningBinId);
        int required = requiredQuantities.get(currentScanningBinId);
        if (scannedTags != null && scannedTags.size() >= required) {
            return;
        }
        if (scannedTags != null && scannedTags.add(epc)) {
            runOnUiThread(() -> {
                updateScannedCountForBin(currentScanningBinId);
                checkAllBinsCompletion();
            });
        }
    }

    private void updateScannedCountForBin(String binId) {
        View binView = binListContainer.findViewWithTag(binId);
        if (binView != null) {
            TextView tvScanned = binView.findViewById(R.id.tvBinScannedQuantity);
            Set<String> tags = scannedTagsPerBin.get(binId);
            if (tags != null) {
                tvScanned.setText("Scanned: " + tags.size());
            }
        }
    }

    private void fetchBinAllocation(String packageId) {
        Log.d(TAG, "[디버깅용] (bin allocation) Authorization 헤더(API_KEY): '" + API_KEY + "'");
        mainHandler.post(() -> {
            progressBar.setVisibility(View.VISIBLE);
            layoutPackageInput.setVisibility(View.GONE);
        });

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        String json = "{\"package_id\": \"" + packageId + "\", \"employee_id\": \"" + employeeId + "\", \"role\": \"" + userRole + "\"}";
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(API_URL_BIN_ALLOCATION)
                .post(body)
                .addHeader("Authorization", API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handleApiError("Failed to connect to server. Please try again.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                Log.d(TAG, "Bin Allocation API Response: " + responseBody);
                
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        
                        if (jsonObject.has("bin_allocation")) {
                            JSONObject binAllocation = jsonObject.getJSONObject("bin_allocation");
                            if (binAllocation.keys().hasNext()) {
                                mainHandler.post(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    displayBinAllocations(packageId, binAllocation);
                                });
                            } else {
                                String message = jsonObject.optString("message", "No bins allocated for this package.");
                                handleApiError(message);
                            }
                        } else if (jsonObject.has("message")) {
                            String message = jsonObject.getString("message");
                            handleApiError(message);
                        } else {
                            handleApiError("Invalid response from server.");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON response", e);
                        handleApiError("An error occurred while parsing the response.");
                    }
                } else {
                    String errorMessage = "Failed to get bin allocation. Server returned an error.";
                     try {
                        JSONObject errorObject = new JSONObject(responseBody);
                        if (errorObject.has("message")) {
                            errorMessage = errorObject.getString("message");
                        }
                    } catch (JSONException e) {
                        // Not a JSON error body, use the generic message
                    }
                    handleApiError(errorMessage);
                }
            }
        });
    }
    
    private void displayBinAllocations(String packageId, JSONObject binAllocation) {
        this.currentPackageId = packageId; // Save the package ID
        layoutPackageInput.setVisibility(View.GONE);
        binAllocationContainer.setVisibility(View.VISIBLE);
        btnConfirmBinning.setVisibility(View.VISIBLE);
        
        tvCurrentPackageId.setText("Package ID: " + packageId);
        binListContainer.removeAllViews();

        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        Iterator<String> keys = binAllocation.keys();
        while(keys.hasNext()) {
            String binId = keys.next();
            try {
                int quantity = binAllocation.getInt(binId);
                requiredQuantities.put(binId, quantity);
                scannedTagsPerBin.put(binId, new HashSet<>());

                View binView = inflater.inflate(R.layout.list_item_bin, binListContainer, false);
                binView.setTag(binId);
                
                TextView tvBinId = binView.findViewById(R.id.tvBinId);
                TextView tvBinQuantity = binView.findViewById(R.id.tvBinQuantity);
                TextView tvBinScannedQuantity = binView.findViewById(R.id.tvBinScannedQuantity);
                Button btnScanBin = binView.findViewById(R.id.btnScanBin);
                
                tvBinId.setText("BIN ID: " + binId);
                tvBinQuantity.setText("Required: " + quantity);
                tvBinScannedQuantity.setText("Scanned: 0"); // Initial value
                
                btnScanBin.setOnClickListener(v -> startOrStopScan(binId, btnScanBin));

                binListContainer.addView(binView);

            } catch (JSONException e) {
                Log.e(TAG, "Error parsing bin allocation for key: " + binId, e);
            }
        }

        // Initial check after loading the list
        checkAllBinsCompletion();
    }

    private void checkAllBinsCompletion() {
        boolean allComplete = true;
        if (requiredQuantities.isEmpty()) {
            allComplete = false;
        } else {
            for (String binId : requiredQuantities.keySet()) {
                int required = requiredQuantities.get(binId);
                Set<String> scanned = scannedTagsPerBin.get(binId);
                if (scanned == null || scanned.size() < required) {
                    allComplete = false;
                    Log.d(TAG, "Bin " + binId + " not complete. Required: " + required + ", Scanned: " + (scanned == null ? 0 : scanned.size()));
                    break;
                }
            }
        }
        Log.d(TAG, "checkAllBinsCompletion: allComplete=" + allComplete);
        btnConfirmBinning.setEnabled(allComplete);
    }

    private void startOrStopScan(String binId, Button clickedButton) {
        if (!isScanning) {
            // Start scanning for this bin
            isScanning = true;
            currentScanningBinId = binId;
            mReader.startInventoryTag();
            clickedButton.setText("Stop");
            setOtherScanButtonsEnabled(false, binId);
        } else {
            // Stop scanning
            isScanning = false;
            currentScanningBinId = null;
            mReader.stopInventory();
            clickedButton.setText("Scan");
            setOtherScanButtonsEnabled(true, null);
            // Check for completion when scan stops
            checkAllBinsCompletion();
        }
    }

    private void setOtherScanButtonsEnabled(boolean enabled, String exceptBinId) {
        for (int i = 0; i < binListContainer.getChildCount(); i++) {
            View child = binListContainer.getChildAt(i);
            String binIdTag = (String) child.getTag();
            if (exceptBinId == null || !exceptBinId.equals(binIdTag)) {
                Button scanButton = child.findViewById(R.id.btnScanBin);
                scanButton.setEnabled(enabled);
            }
        }
    }

    private void confirmBinningOnServer() {
        Log.d(TAG, "confirmBinningOnServer() called. currentPackageId=" + currentPackageId);
        Log.d(TAG, "[디버깅용] Authorization 헤더(API_KEY): '" + API_KEY + "'");
        mainHandler.post(() -> progressBar.setVisibility(View.VISIBLE));

        String url = String.format(API_URL_CLOSE_BINNING, this.currentPackageId);
        Log.d(TAG, "Close binning URL: " + url);

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        String json = "{\"employee_id\": \"" + employeeId + "\", \"role\": \"" + userRole + "\"}";
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Close binning API call failed", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BinningActivity.this, "Failed to confirm binning: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                Log.d(TAG, "Close binning API response: " + responseBody);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            String message = jsonObject.optString("message", "Binning completed successfully.");
                            Toast.makeText(BinningActivity.this, message, Toast.LENGTH_LONG).show();
                            resetToInitialState();
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing close binning response", e);
                            Toast.makeText(BinningActivity.this, "Error parsing response.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Close binning failed. Code: " + response.code() + ", Body: " + responseBody);
                        Toast.makeText(BinningActivity.this, "Failed to confirm binning. Server returned error: " + responseBody, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void resetToInitialState() {
        binAllocationContainer.setVisibility(View.GONE);
        btnConfirmBinning.setVisibility(View.GONE);
        layoutPackageInput.setVisibility(View.VISIBLE);
        etPackageId.setText("");

        currentPackageId = "";
        scannedTagsPerBin.clear();
        requiredQuantities.clear();
        btnConfirmBinning.setEnabled(false);
    }

    private void handleApiError(String message) {
        mainHandler.post(() -> {
            progressBar.setVisibility(View.GONE);
            layoutPackageInput.setVisibility(View.VISIBLE);
            Toast.makeText(BinningActivity.this, message, Toast.LENGTH_LONG).show();
        });
    }
} 