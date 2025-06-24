package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
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
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PackingActivity extends AppCompatActivity {

    private static final String TAG = "PackingActivity";
    private static final String API_URL_START = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/packing/start";
    private static final String API_URL_CLOSE = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/packing/%s/close";
    private String API_KEY = "";
    private String currentPickSlipId = "";
    private String userRole = "";
    private String employeeId = "";

    private Button btnBack;
    private TextView tvUserInfo;
    private LinearLayout inputContainer;
    private Button btnScanPackingZone;
    private EditText etPackingZone;
    private Button btnConfirmManual;
    private ProgressBar progressBar;
    private ScrollView pickSlipDetailsContainer;
    private Button btnPackingComplete;
    private LinearLayout itemsContainer;
    
    private TextView tvPickSlipId, tvPackerId, tvPackingZone, tvCustomerId, 
                     tvPackingStartDate, tvPickSlipCreatedDate, tvRequestedDeliveryDate, tvPickSlipStatus;

    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_packing);

        if (getIntent().hasExtra("api_key")) {
            API_KEY = getIntent().getStringExtra("api_key");
        }
        if (getIntent().hasExtra("role")) {
            userRole = getIntent().getStringExtra("role");
        }
        if (getIntent().hasExtra("employee_id")) {
            employeeId = getIntent().getStringExtra("employee_id");
        }

        btnBack = findViewById(R.id.btnBack);
        tvUserInfo = findViewById(R.id.tvUserInfo);
        inputContainer = findViewById(R.id.inputContainer);
        btnScanPackingZone = findViewById(R.id.btnScanPackingZone);
        etPackingZone = findViewById(R.id.etPackingZone);
        btnConfirmManual = findViewById(R.id.btnConfirmManual);
        progressBar = findViewById(R.id.progressBar);
        pickSlipDetailsContainer = findViewById(R.id.pickSlipDetailsContainer);
        btnPackingComplete = findViewById(R.id.btnPackingComplete);
        itemsContainer = findViewById(R.id.itemsContainer);
        
        tvPickSlipId = findViewById(R.id.tvPickSlipId);
        tvPackerId = findViewById(R.id.tvPackerId);
        tvPackingZone = findViewById(R.id.tvPackingZone);
        tvCustomerId = findViewById(R.id.tvCustomerId);
        tvPackingStartDate = findViewById(R.id.tvPackingStartDate);
        tvPickSlipCreatedDate = findViewById(R.id.tvPickSlipCreatedDate);
        tvRequestedDeliveryDate = findViewById(R.id.tvRequestedDeliveryDate);
        tvPickSlipStatus = findViewById(R.id.tvPickSlipStatus);

        setupHeader();
        btnScanPackingZone.setOnClickListener(v -> startBarcodeScanning());
        
        btnConfirmManual.setOnClickListener(v -> {
            String packingZone = etPackingZone.getText().toString().trim();
            if (!packingZone.isEmpty()) {
                fetchPickSlip(packingZone);
            } else {
                Toast.makeText(this, "Please enter a packing zone.", Toast.LENGTH_SHORT).show();
            }
        });

        btnPackingComplete.setOnClickListener(v -> {
            if (currentPickSlipId != null && !currentPickSlipId.isEmpty()) {
                closePickSlip();
            } else {
                Toast.makeText(this, "No Pick Slip ID found.", Toast.LENGTH_SHORT).show();
            }
        });
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

    private void startBarcodeScanning() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(CustomCaptureActivity.class);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("Scan Packing Zone");
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
                String packingZone = result.getContents();
                // Toast.makeText(this, "Scanned: " + packingZone, Toast.LENGTH_LONG).show();
                fetchPickSlip(packingZone);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void fetchPickSlip(String packingZone) {
        mainHandler.post(() -> {
            progressBar.setVisibility(View.VISIBLE);
            inputContainer.setVisibility(View.GONE);
        });

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        String json = "{\"packing_zone\": \"" + packingZone + "\", \"employee_id\": \"" + employeeId + "\", \"role\": \"" + userRole + "\"}";
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(API_URL_START)
                .post(body)
                .addHeader("Authorization", API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        Log.d(TAG, "Request URL: " + request.url());
        Log.d(TAG, "Request Headers: " + request.headers());
        Log.d(TAG, "Request Body: " + json);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "API call failed", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    inputContainer.setVisibility(View.VISIBLE);
                    Toast.makeText(PackingActivity.this, "Failed to fetch data. Please try again.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "API call unsuccessful. Code: " + response.code());
                     mainHandler.post(() -> {
                        progressBar.setVisibility(View.GONE);
                        inputContainer.setVisibility(View.VISIBLE);
                        Toast.makeText(PackingActivity.this, "No pick slip to process.", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                final String responseBody = response.body().string();
                Log.d(TAG, "API Response: " + responseBody);

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);

                        if(jsonObject.has("message") && "No pick slip to process.".equals(jsonObject.getString("message"))) {
                             inputContainer.setVisibility(View.VISIBLE);
                             Toast.makeText(PackingActivity.this, "No pick slip to process for this zone.", Toast.LENGTH_LONG).show();
                             return;
                        }

                        currentPickSlipId = jsonObject.getString("pick_slip_id");
                        tvPickSlipId.setText("Pick Slip ID: " + currentPickSlipId);
                        tvPackerId.setText("Packer ID: " + jsonObject.getString("packer_id"));
                        tvPackingZone.setText("Packing Zone: " + jsonObject.getString("packing_zone"));
                        tvCustomerId.setText("Customer ID: " + jsonObject.getString("customer_id"));
                        tvPackingStartDate.setText("Packing Start: " + jsonObject.getString("packing_start_date"));
                        tvPickSlipCreatedDate.setText("Pick Slip Created: " + jsonObject.getString("pick_slip_created_date"));
                        tvRequestedDeliveryDate.setText("Requested Delivery: " + jsonObject.getString("requested_delivery_date"));
                        tvPickSlipStatus.setText("Status: " + jsonObject.getString("pick_slip_status"));
                        
                        itemsContainer.removeAllViews();
                        JSONArray items = jsonObject.getJSONArray("items");
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            TextView itemView = new TextView(PackingActivity.this);
                            itemView.setText("Product: " + item.getString("productId") + ", Quantity: " + item.getString("quantity"));
                            itemView.setPadding(0, 4, 0, 4);
                            itemsContainer.addView(itemView);
                        }
                        
                        pickSlipDetailsContainer.setVisibility(View.VISIBLE);
                        btnPackingComplete.setVisibility(View.VISIBLE);

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse JSON", e);
                        inputContainer.setVisibility(View.VISIBLE);
                        Toast.makeText(PackingActivity.this, "An error occurred while parsing the response.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void closePickSlip() {
        mainHandler.post(() -> progressBar.setVisibility(View.VISIBLE));

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        String json = "{\"employee_id\": \"" + employeeId + "\", \"role\": \"" + userRole + "\"}";
        RequestBody body = RequestBody.create(json, JSON);

        String url = String.format(API_URL_CLOSE, currentPickSlipId);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Close Pick Slip API call failed", e);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(PackingActivity.this, "Failed to close pick slip. Please try again.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                Log.d(TAG, "Close API Response: " + responseBody);

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonObject = new JSONObject(responseBody);
                            String message = jsonObject.optString("message", "Packing completed successfully.");
                            Toast.makeText(PackingActivity.this, message, Toast.LENGTH_LONG).show();
                            resetToInitialState();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse close response", e);
                            Toast.makeText(PackingActivity.this, "An error occurred while parsing the close response.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(PackingActivity.this, "Failed to close pick slip. Server returned an error.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void resetToInitialState() {
        pickSlipDetailsContainer.setVisibility(View.GONE);
        btnPackingComplete.setVisibility(View.GONE);
        inputContainer.setVisibility(View.VISIBLE);
        etPackingZone.setText("");
        currentPickSlipId = "";
    }
} 