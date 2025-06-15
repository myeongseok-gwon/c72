package com.example.uhf.iot;

import android.content.Context;
import android.util.Log;

import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback.MessageDeliveryStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class AwsIotManager {
    private static final String TAG = "AwsIotManager";
    
    // AWS 설정값들
    private static final String CUSTOMER_SPECIFIC_ENDPOINT = AwsConfigHelper.IOT_ENDPOINT;
    private static final String TOPIC_PREFIX = AwsConfigHelper.TOPIC_PREFIX;
    private static final String CLIENT_ID_PREFIX = AwsConfigHelper.CLIENT_ID_PREFIX;
    
    private AWSIotMqttManager mqttManager;
    private String clientId;
    private Context context;
    private boolean isConnected = false;
    
    public interface ConnectionCallback {
        void onConnectionSuccess();
        void onConnectionFailure(Exception exception);
    }
    
    public interface PublishCallback {
        void onPublishSuccess();
        void onPublishFailure(Exception exception);
    }
    
    public AwsIotManager(Context context) {
        this.context = context;
        this.clientId = CLIENT_ID_PREFIX + UUID.randomUUID().toString();
        initializeMqttManager();
    }
    
    private void initializeMqttManager() {
        // MQTT 매니저 초기화
        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);
        mqttManager.setAutoReconnect(true);
        
        Log.i(TAG, "MQTT 매니저 초기화 완료");
        Log.i(TAG, "클라이언트 ID: " + clientId);
        Log.i(TAG, "엔드포인트: " + CUSTOMER_SPECIFIC_ENDPOINT);
    }
    
    /**
     * 사전 생성된 KeyStore를 사용하여 AWS IoT Core에 연결
     */
    public void connectWithCertificate(ConnectionCallback callback) {
        try {
            Log.i(TAG, "AWS IoT Core 연결 시작...");
            
            // 사전 생성된 KeyStore 파일 사용
            KeyStore keyStore = loadPrebuiltKeyStore();
            
            Log.i(TAG, "KeyStore 로드 완료");
            
            // AWS IoT SDK에서 KeyStore를 사용하여 연결
            mqttManager.connect(keyStore, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {
                    Log.i(TAG, "연결 상태: " + status.toString());
                    
                    switch (status) {
                        case Connecting:
                            Log.i(TAG, "AWS IoT에 연결 중...");
                            break;
                        case Connected:
                            Log.i(TAG, "AWS IoT 연결 성공!");
                            isConnected = true;
                            if (callback != null) {
                                callback.onConnectionSuccess();
                            }
                            break;
                        case Reconnecting:
                            Log.i(TAG, "AWS IoT 재연결 중...");
                            isConnected = false;
                            break;
                        case ConnectionLost:
                            Log.w(TAG, "AWS IoT 연결 끊어짐");
                            isConnected = false;
                            break;
                    }
                    
                    if (throwable != null) {
                        Log.e(TAG, "연결 오류: " + throwable.getMessage());
                        throwable.printStackTrace();
                        isConnected = false;
                        if (callback != null) {
                            callback.onConnectionFailure(new Exception(throwable));
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "AWS IoT 연결 실패: " + e.getMessage());
            e.printStackTrace();
            if (callback != null) {
                callback.onConnectionFailure(e);
            }
        }
    }
    
    /**
     * 사전 생성된 PKCS12 KeyStore 파일 로드
     */
    private KeyStore loadPrebuiltKeyStore() throws Exception {
        try {
            // raw 폴더에서 PKCS12 KeyStore 로드
            InputStream keystoreStream = context.getResources().openRawResource(
                context.getResources().getIdentifier("iot_keystore", "raw", context.getPackageName())
            );
            
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(keystoreStream, "password".toCharArray());
            keystoreStream.close();
            
            Log.i(TAG, "PKCS12 KeyStore 로드 성공");
            return keyStore;
            
        } catch (Exception e) {
            Log.e(TAG, "PKCS12 KeyStore 로드 실패: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * TQ 시스템용 RFID 스캔 이벤트를 AWS IoT Core에 발행 (더미 Package ID)
     */
    public void publishRfidScanEvent(String epcId, String rssi, String timestamp, PublishCallback callback) {
        publishRfidScanEvent(epcId, rssi, timestamp, "PKG-" + System.currentTimeMillis(), callback);
    }
    
    /**
     * TQ 시스템용 RFID 스캔 이벤트를 AWS IoT Core에 발행 (실제 Package ID 사용)
     */
    public void publishRfidScanEvent(String epcId, String rssi, String timestamp, String packageId, PublishCallback callback) {
        if (!isConnected) {
            Log.w(TAG, "AWS IoT에 연결되지 않음. 메시지를 발행할 수 없습니다.");
            if (callback != null) {
                callback.onPublishFailure(new Exception("Not connected to AWS IoT"));
            }
            return;
        }
        
        try {
            // TQ 시스템 형식의 JSON 메시지 생성
            JSONObject message = new JSONObject();
            message.put("rfid_id", epcId);  // 스캔한 RFID ID
            message.put("package_id", packageId); // 실제 Package ID 사용
            
            // 현재 시간을 "YYYY-MM-DD HH:mm:ss" 형식으로 추가
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            message.put("tq_date", sdf.format(new Date()));
            
            // 부가 정보 (선택적)
            JSONObject metadata = new JSONObject();
            metadata.put("device_id", clientId);
            metadata.put("rssi", rssi);
            metadata.put("scan_timestamp", timestamp);
            metadata.put("scan_type", "UHF_RFID");
            message.put("metadata", metadata);
            
            String topic = AwsConfigHelper.TQ_TOPIC;
            String payload = message.toString();
            
            Log.i(TAG, "========== TQ RFID 이벤트 발송 ==========");
            Log.i(TAG, "토픽: " + topic);
            Log.i(TAG, "메시지: " + payload);
            Log.i(TAG, "Package ID: " + packageId);
            Log.i(TAG, "클라이언트 ID: " + clientId);
            Log.i(TAG, "============================================");
            
            // MQTT 메시지 발행
            mqttManager.publishString(payload, topic, AWSIotMqttQos.QOS0, new AWSIotMqttMessageDeliveryCallback() {
                @Override
                public void statusChanged(MessageDeliveryStatus status, Object userData) {
                    Log.i(TAG, "메시지 발행 상태: " + status.toString());
                    
                    if (status == MessageDeliveryStatus.Success) {
                        Log.i(TAG, "TQ RFID 스캔 이벤트 발행 성공! Package: " + packageId);
                        if (callback != null) {
                            callback.onPublishSuccess();
                        }
                    } else {
                        Log.e(TAG, "TQ RFID 스캔 이벤트 발행 실패: " + status.toString());
                        if (callback != null) {
                            callback.onPublishFailure(new Exception("Publish failed: " + status.toString()));
                        }
                    }
                }
            }, null);
            
        } catch (JSONException e) {
            Log.e(TAG, "JSON 생성 오류: " + e.getMessage());
            if (callback != null) {
                callback.onPublishFailure(e);
            }
        }
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public void disconnect() {
        if (mqttManager != null) {
            try {
                mqttManager.disconnect();
                isConnected = false;
                Log.i(TAG, "AWS IoT 연결 해제됨");
            } catch (Exception e) {
                Log.e(TAG, "연결 해제 오류: " + e.getMessage());
            }
        }
    }
}