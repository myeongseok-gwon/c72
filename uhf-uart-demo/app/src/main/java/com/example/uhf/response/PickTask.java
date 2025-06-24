package com.example.uhf.response;

import com.google.gson.annotations.SerializedName;

import java.util.HashSet;
import java.util.Set;

public class PickTask {

    @SerializedName("bin_id")
    private String binId;

    @SerializedName("product_id")
    private String productId;

    @SerializedName("quantity")
    private String quantity;

    private transient int scannedQuantity = 0;
    private transient Set<String> scannedEpcs = new HashSet<>();

    public String getBinId() {
        return binId;
    }

    public String getProductId() {
        return productId;
    }

    public String getQuantity() {
        return quantity;
    }

    public int getScannedQuantity() {
        return scannedQuantity;
    }

    public boolean addScannedEpc(String epc) {
        if (scannedEpcs.add(epc)) {
            scannedQuantity = scannedEpcs.size();
            return true;
        }
        return false;
    }

    public void clearScanned() {
        scannedEpcs.clear();
        scannedQuantity = 0;
    }
} 