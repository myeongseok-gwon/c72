package com.example.uhf.api;

import android.os.AsyncTask;
import android.util.Log;

import com.example.uhf.response.ClosePickOrderResponse;
import com.example.uhf.response.PickOrderResponse;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * API Client for Cargoops TQ System
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    private static final String BASE_URL = "https://ozw3p7h26e.execute-api.us-east-2.amazonaws.com/Prod";
    private static final int TIMEOUT_MS = 15000; // 15 seconds
    
    public interface ApiKeyCallback {
        void onSuccess(ApiKeyResponse response);
        void onError(String error);
    }
    
    public interface PickOrderCallback {
        void onSuccess(PickOrderResponse response);
        void onError(String error);
    }

    public interface ClosePickOrderCallback {
        void onSuccess(ClosePickOrderResponse response);
        void onError(String error);
    }
    
    /**
     * API Key Response model
     */
    public static class ApiKeyResponse {
        public String role;
        public String employeeId;
        public String apiKey;
        
        public ApiKeyResponse(String role, String employeeId, String apiKey) {
            this.role = role;
            this.employeeId = employeeId;
            this.apiKey = apiKey;
        }
        
        public boolean isValid() {
            return role != null && !role.isEmpty() && 
                   employeeId != null && !employeeId.isEmpty();
        }
    }
    
    /**
     * Validate API Key with server
     */
    public static void validateApiKey(String apiKey, ApiKeyCallback callback) {
        new ValidateApiKeyTask(apiKey, callback).execute();
    }
    
    /**
     * Get next pick order
     */
    public static void getNextPickOrder(String apiKey, String employeeId, String userRole, PickOrderCallback callback) {
        new GetNextPickOrderTask(apiKey, employeeId, userRole, callback).execute();
    }

    /**
     * Close a pick order
     */
    public static void closePickOrder(String apiKey, String pickOrderId, String employeeId, String userRole, ClosePickOrderCallback callback) {
        new ClosePickOrderTask(apiKey, pickOrderId, employeeId, userRole, callback).execute();
    }
    
    private static class ValidateApiKeyTask extends AsyncTask<Void, Void, ApiKeyResponse> {
        private final String apiKey;
        private final ApiKeyCallback callback;
        private String errorMessage = null;
        
        public ValidateApiKeyTask(String apiKey, ApiKeyCallback callback) {
            this.apiKey = apiKey;
            this.callback = callback;
        }
        
        @Override
        protected ApiKeyResponse doInBackground(Void... voids) {
            String urlString = BASE_URL + "/api-key?api_key=" + apiKey;
            Log.d(TAG, "🔑 Validating API Key with GET request: " + urlString);
            
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "CargoopsRFIDApp/1.0");
                
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "📡 Response Code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    String responseBody = response.toString();
                    Log.d(TAG, "📄 API response data: " + responseBody);
                    
                    JSONObject jsonObject = new JSONObject(responseBody);
                    String role = jsonObject.optString("role", null);
                    String returnedApiKey = jsonObject.optString("api_key", apiKey);
                    String employeeId = jsonObject.optString("employee_id", null);
                    if (role != null && !role.isEmpty()) {
                        Log.d(TAG, "✅ Validation SUCCESS: role=" + role);
                        return new ApiKeyResponse(role, employeeId, returnedApiKey);
                    } else {
                        errorMessage = "Invalid response format - missing role";
                        Log.e(TAG, "❌ " + errorMessage);
                        return null;
                    }
                } else {
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        errorMessage = "HTTP " + responseCode + ": " + errorResponse.toString();
                    } catch (Exception e) {
                        errorMessage = "HTTP " + responseCode + ": Error reading error stream.";
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    }
                    Log.e(TAG, "❌ API Error: " + errorMessage);
                    return null;
                }
            } catch (Exception e) {
                errorMessage = "API request failed: " + e.getMessage();
                Log.e(TAG, "❌ " + errorMessage, e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        
        @Override
        protected void onPostExecute(ApiKeyResponse result) {
            if (result != null && result.isValid()) {
                callback.onSuccess(result);
            } else {
                if (errorMessage != null) {
                    callback.onError(errorMessage);
                } else {
                    callback.onError("Unknown error during API Key validation");
                }
            }
        }
    }

    private static class GetNextPickOrderTask extends AsyncTask<Void, Void, PickOrderResponse> {
        private final String apiKey;
        private final String employeeId;
        private final String userRole;
        private final PickOrderCallback callback;
        private String errorMessage = null;

        public GetNextPickOrderTask(String apiKey, String employeeId, String userRole, PickOrderCallback callback) {
            this.apiKey = apiKey;
            this.employeeId = employeeId;
            this.userRole = userRole;
            this.callback = callback;
        }

        @Override
        protected PickOrderResponse doInBackground(Void... voids) {
            String urlString = BASE_URL + "/next-pick-order?employee_id=" + employeeId + "&role=" + userRole;
            Log.d(TAG, "🚚 Getting next pick order with GET request: " + urlString);

            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "CargoopsRFIDApp/1.0");
                connection.setRequestProperty("Authorization", apiKey);

                Log.d(TAG, "--- API Request Details ---");
                Log.d(TAG, "URL: " + urlString);
                Log.d(TAG, "Method: GET");
                Log.d(TAG, "Authorization: " + apiKey);
                Log.d(TAG, "--------------------------");

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "📡 Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String responseBody = response.toString();
                    Log.d(TAG, "📄 API response data: " + responseBody);

                    Gson gson = new Gson();
                    return gson.fromJson(responseBody, PickOrderResponse.class);
                } else {
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        errorMessage = "HTTP " + responseCode + ": " + errorResponse.toString();
                    } catch (Exception e) {
                        errorMessage = "HTTP " + responseCode + ": Error reading error stream.";
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    }
                    Log.e(TAG, "❌ API Error: " + errorMessage);
                    return null;
                }
            } catch (Exception e) {
                errorMessage = "API request failed: " + e.getMessage();
                Log.e(TAG, "❌ " + errorMessage, e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(PickOrderResponse result) {
            if (result != null) {
                callback.onSuccess(result);
            } else {
                if (errorMessage != null) {
                    callback.onError(errorMessage);
                } else {
                    callback.onError("Unknown error during fetching next pick order");
                }
            }
        }
    }

    private static class ClosePickOrderTask extends AsyncTask<Void, Void, ClosePickOrderResponse> {
        private final String apiKey;
        private final String pickOrderId;
        private final String employeeId;
        private final String userRole;
        private final ClosePickOrderCallback callback;
        private String errorMessage = null;

        public ClosePickOrderTask(String apiKey, String pickOrderId, String employeeId, String userRole, ClosePickOrderCallback callback) {
            this.apiKey = apiKey;
            this.pickOrderId = pickOrderId;
            this.employeeId = employeeId;
            this.userRole = userRole;
            this.callback = callback;
        }

        @Override
        protected ClosePickOrderResponse doInBackground(Void... voids) {
            String urlString = BASE_URL + "/pick-orders/" + pickOrderId + "/close";
            Log.d(TAG, "📦 Closing pick order with POST request: " + urlString);

            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestProperty("Authorization", apiKey);
                connection.setRequestProperty("Content-Type", "application/json");

                // POST body에 employee_id, role 추가
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("employee_id", employeeId);
                jsonBody.put("role", userRole);
                String bodyString = jsonBody.toString();
                connection.setDoOutput(true);
                OutputStream os = connection.getOutputStream();
                os.write(bodyString.getBytes("UTF-8"));
                os.close();

                Log.d(TAG, "--- API Request Details ---");
                Log.d(TAG, "URL: " + urlString);
                Log.d(TAG, "Method: POST");
                Log.d(TAG, "Authorization: " + apiKey);
                Log.d(TAG, "--------------------------");

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "📡 Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String responseBody = response.toString();
                    Log.d(TAG, "📄 API response data: " + responseBody);

                    Gson gson = new Gson();
                    return gson.fromJson(responseBody, ClosePickOrderResponse.class);
                } else {
                    BufferedReader reader = null;
                    try {
                        reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        errorMessage = "HTTP " + responseCode + ": " + errorResponse.toString();
                    } catch (Exception e) {
                        errorMessage = "HTTP " + responseCode + ": Error reading error stream.";
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                    }
                    Log.e(TAG, "❌ API Error: " + errorMessage);
                    return null;
                }
            } catch (Exception e) {
                errorMessage = "API request failed: " + e.getMessage();
                Log.e(TAG, "❌ " + errorMessage, e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(ClosePickOrderResponse result) {
            if (result != null) {
                callback.onSuccess(result);
            } else {
                if (errorMessage != null) {
                    callback.onError(errorMessage);
                } else {
                    callback.onError("Unknown error during closing pick order");
                }
            }
        }
    }
}