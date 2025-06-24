package com.example.uhf.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DispatchActivity extends AppCompatActivity {

    private static final String TAG = "DispatchActivity";
    private static final String API_URL_DISPATCH = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/pick-slips/%s/dispatch";
    private String API_KEY = "";
    private String userRole = "";
    private String employeeId = "";

    private Button btnBack;
    private TextView tvUserInfo;
    private LinearLayout inputContainer;
    private Button btnScanPickSlip;
    private EditText etPickSlipId;
    private Button btnConfirmDispatch;
    private ProgressBar progressBar;

    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dispatch);

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
        inputContainer = findViewById(R.id.dispatchInputContainer);
        btnScanPickSlip = findViewById(R.id.btnScanPickSlip);
        etPickSlipId = findViewById(R.id.etPickSlipId);
        btnConfirmDispatch = findViewById(R.id.btnConfirmDispatch);
        progressBar = findViewById(R.id.dispatchProgressBar);

        setupHeader();
        btnScanPickSlip.setOnClickListener(v -> startBarcodeScanning());

        btnConfirmDispatch.setOnClickListener(v -> {
            String pickSlipId = etPickSlipId.getText().toString().trim();
            if (!TextUtils.isEmpty(pickSlipId)) {
                triggerDispatch(pickSlipId);
            } else {
                Toast.makeText(this, "Please enter a Pick Slip ID.", Toast.LENGTH_SHORT).show();
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
        integrator.setPrompt("Scan Pick Slip ID");
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
                String pickSlipId = result.getContents();
                etPickSlipId.setText(pickSlipId);
                triggerDispatch(pickSlipId);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void triggerDispatch(String pickSlipId) {
        setLoadingState(true);

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        String json = "{\"employee_id\": \"" + employeeId + "\", \"role\": \"" + userRole + "\"}";
        RequestBody body = RequestBody.create(json, JSON);
        String url = String.format(API_URL_DISPATCH, pickSlipId);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        Log.d(TAG, "Dispatch Request URL: " + request.url());
        Log.d(TAG, "Dispatch Request Headers: " + request.headers());

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Dispatch API call failed", e);
                mainHandler.post(() -> {
                    setLoadingState(false);
                    showResultDialog("Error", "Dispatch request failed. Please try again.");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body().string();
                Log.d(TAG, "Dispatch API Response: " + responseBody);

                mainHandler.post(() -> {
                    setLoadingState(false);
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        String message = jsonObject.optString("message", "An unknown response was received.");
                        String title = response.isSuccessful() ? "Success" : "Error";
                        showResultDialog(title, message);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse dispatch response", e);
                        showResultDialog("Error", "An error occurred while parsing the response.");
                    }
                });
            }
        });
    }
    
    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            inputContainer.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            inputContainer.setVisibility(View.VISIBLE);
        }
    }

    private void showResultDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    resetUi(); // Reset UI after user acknowledges the dialog
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }
    
    private void resetUi() {
        etPickSlipId.setText("");
    }
} 