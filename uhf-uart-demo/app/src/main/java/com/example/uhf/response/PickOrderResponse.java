package com.example.uhf.response;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PickOrderResponse {

    @SerializedName("pick_slip_id")
    private String pickSlipId;

    @SerializedName("pick_order_id")
    private String pickOrderId;

    @SerializedName("picker_id")
    private String pickerId;

    @SerializedName("picking_zone")
    private String pickingZone;

    @SerializedName("pick_order_status")
    private String pickOrderStatus;

    @SerializedName("pick_task")
    private List<PickTask> pickTask;

    @SerializedName("order_created_date")
    private String orderCreatedDate;

    public String getPickSlipId() {
        return pickSlipId;
    }

    public String getPickOrderId() {
        return pickOrderId;
    }

    public String getPickerId() {
        return pickerId;
    }

    public String getPickingZone() {
        return pickingZone;
    }

    public String getPickOrderStatus() {
        return pickOrderStatus;
    }

    public List<PickTask> getPickTask() {
        return pickTask;
    }

    public String getOrderCreatedDate() {
        return orderCreatedDate;
    }
} 