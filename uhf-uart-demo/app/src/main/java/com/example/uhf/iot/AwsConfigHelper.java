package com.example.uhf.iot;

/**
 * AWS IoT Core 설정 관리 클래스
 * 실제 프로젝트에서는 이 값들을 실제 AWS 설정으로 변경해야 합니다.
 */
public class AwsConfigHelper {
    
    // AWS IoT Core 엔드포인트 (start.sh에서 확인됨)
    public static final String IOT_ENDPOINT = "avt319l6989mq-ats.iot.us-east-2.amazonaws.com";
    
    // AWS 리전
    public static final String AWS_REGION = "us-east-2";
    
    // TODO: 실제 Cognito Identity Pool ID로 변경하세요 (필요한 경우)
    public static final String COGNITO_POOL_ID = "region:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx";
    
    // MQTT 토픽 설정 - TQ 시스템용
    public static final String TOPIC_PREFIX = "rfid/";
    public static final String TQ_TOPIC = "rfid/tq";  // 메인 토픽
    public static final String EVENTS_TOPIC = TOPIC_PREFIX + "scanner/events";  // 기존 토픽 (선택적)
    public static final String BATCH_TOPIC = TOPIC_PREFIX + "scanner/batch";
    public static final String STATUS_TOPIC = TOPIC_PREFIX + "scanner/status";
    
    // 인증서 파일 이름 (raw 폴더에 위치)
    public static final String KEYSTORE_NAME = "iot_keystore"; // .p12 파일
    public static final String KEYSTORE_PASSWORD = "password"; // Connection Kit에서 제공된 패스워드
    
    // 클라이언트 ID 접두사
    public static final String CLIENT_ID_PREFIX = "c72-rfid-scanner-";
    
    /**
     * AWS IoT Core 설정이 올바른지 검증
     */
    public static boolean isConfigurationValid() {
        return !IOT_ENDPOINT.contains("your-iot-endpoint");
    }
    
    /**
     * 설정 상태를 문자열로 반환
     */
    public static String getConfigurationStatus() {
        if (isConfigurationValid()) {
            return "AWS 설정이 완료되었습니다. 엔드포인트: " + IOT_ENDPOINT;
        } else {
            return "AWS 설정을 완료해주세요. AwsConfigHelper 클래스의 상수값들을 실제 AWS 값으로 변경하세요.";
        }
    }
}