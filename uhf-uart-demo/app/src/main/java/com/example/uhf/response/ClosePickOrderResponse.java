package com.example.uhf.response;

import com.google.gson.annotations.SerializedName;

public class ClosePickOrderResponse {

    @SerializedName("message")
    private String message;

    @SerializedName("pick_slip_status")
    private String pickSlipStatus;

    public String getMessage() {
        return message;
    }

    public String getPickSlipStatus() {
        return pickSlipStatus;
    }
} 