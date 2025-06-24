package com.example.uhf.util;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * API Testing Utility for debugging API connectivity issues
 */
public class ApiTestUtil {
    private static final String TAG = "ApiTestUtil";
    
    /**
     * Test basic connectivity to the API endpoint
     */
    public static void testApiConnectivity() {
        new Thread(() -> {
            try {
                String baseUrl = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod";
                
                // Test 1: Basic OPTIONS request to check CORS
                testOptionsRequest(baseUrl + "/api-key");
                
                // Test 2: Basic GET request to root
                testGetRequest(baseUrl);
                
                // Test 3: Test specific endpoint without parameters
                testGetRequest(baseUrl + "/api-key");
                
            } catch (Exception e) {
                Log.e(TAG, "❌ API connectivity test failed", e);
            }
        }).start();
    }
    
    private static void testOptionsRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "🔍 Testing OPTIONS request to: " + urlString);
            
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("OPTIONS");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "📡 OPTIONS Response Code: " + responseCode);
            
            // Log response headers
            connection.getHeaderFields().forEach((key, value) -> {
                Log.d(TAG, "📋 Header - " + key + ": " + value);
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ OPTIONS request failed: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    private static void testGetRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "🔍 Testing GET request to: " + urlString);
            
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "CargoopsRFIDApp/1.0");
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "📡 GET Response Code: " + responseCode);
            
            // Read response or error
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "📄 Response Body: " + response.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "❌ GET request failed: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Test specific API key validation methods
     */
    public static void testApiKeyMethods(String apiKey) {
        new Thread(() -> {
            Log.d(TAG, "🔑 Testing API key methods for: " + apiKey);
            
            // Method 1: Query parameter
            testApiKeyMethod(
                "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod/api_key?api_key=" + apiKey,
                "GET with query parameter",
                null
            );
            
        }).start();
    }
    
    private static void testApiKeyMethod(String urlString, String methodName, String apiKeyHeader) {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "🔍 Testing " + methodName + ": " + urlString);
            
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CargoopsRFIDApp/1.0");
            
            if (apiKeyHeader != null) {
                connection.setRequestProperty("x-api-key", apiKeyHeader);
            }
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "📡 " + methodName + " - Response Code: " + responseCode);
            
            // Read response
            BufferedReader reader;
            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }
            
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            Log.d(TAG, "📄 " + methodName + " - Response: " + response.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "❌ " + methodName + " failed: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}