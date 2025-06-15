package com.example.uhf.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.uhf.R;
import com.example.uhf.iot.AwsIotManager;

/**
 * TQ 시스템 메인 액티비티
 * TQ, BIN, PICK 3가지 주요 기능 선택
 */
public class TQMainActivity extends AppCompatActivity {
    private static final String TAG = "TQMainActivity";
    
    private Button btnTQ, btnBIN, btnPICK;
    private TextView tvIotStatus;
    private AwsIotManager awsIotManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        initializeAwsIot();
        setupClickListeners();
    }
    
    private void initializeViews() {
        btnTQ = findViewById(R.id.btnTQ);
        btnBIN = findViewById(R.id.btnBIN);
        btnPICK = findViewById(R.id.btnPICK);
        tvIotStatus = findViewById(R.id.tvIotStatus);
    }
    
    private void initializeAwsIot() {
        // AWS IoT 연결 초기화
        awsIotManager = new AwsIotManager(this);
        updateIotStatus("연결 중...", Color.parseColor("#f39c12"));
        
        awsIotManager.connectWithCertificate(new AwsIotManager.ConnectionCallback() {
            @Override
            public void onConnectionSuccess() {
                runOnUiThread(() -> {
                    updateIotStatus("연결됨", Color.parseColor("#27ae60"));
                    Toast.makeText(TQMainActivity.this, "AWS IoT 연결 성공!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onConnectionFailure(Exception exception) {
                runOnUiThread(() -> {
                    updateIotStatus("연결 실패", Color.parseColor("#e74c3c"));
                    Toast.makeText(TQMainActivity.this, "AWS IoT 연결 실패: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void updateIotStatus(String status, int color) {
        tvIotStatus.setText(status);
        tvIotStatus.setTextColor(color);
    }
    
    private void setupClickListeners() {
        // TQ 버튼 클릭 - TQ 액티비티로 이동
        btnTQ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(TQMainActivity.this, TQActivity.class);
                startActivity(intent);
            }
        });
        
        // BIN 버튼 클릭 - 아직 구현되지 않음
        btnBIN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(TQMainActivity.this, "BIN 기능은 아직 구현되지 않았습니다.", Toast.LENGTH_SHORT).show();
            }
        });
        
        // PICK 버튼 클릭 - 아직 구현되지 않음
        btnPICK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(TQMainActivity.this, "PICK 기능은 아직 구현되지 않았습니다.", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 기존 UHF 스캐너로 이동 (디버깅용)
        btnPICK.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(TQMainActivity.this, UHFMainActivity.class);
                startActivity(intent);
                return true;
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (awsIotManager != null) {
            awsIotManager.disconnect();
        }
    }
}