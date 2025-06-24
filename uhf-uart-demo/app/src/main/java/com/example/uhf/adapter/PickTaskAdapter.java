package com.example.uhf.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.response.PickTask;

import java.util.List;

public class PickTaskAdapter extends RecyclerView.Adapter<PickTaskAdapter.PickTaskViewHolder> {

    private List<PickTask> pickTasks;
    private OnScanButtonClickListener listener;

    public interface OnScanButtonClickListener {
        void onScanButtonClick(int position);
    }

    public PickTaskAdapter(List<PickTask> pickTasks, OnScanButtonClickListener listener) {
        this.pickTasks = pickTasks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PickTaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.pick_task_item, parent, false);
        return new PickTaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PickTaskViewHolder holder, int position) {
        PickTask pickTask = pickTasks.get(position);
        holder.binId.setText("Bin ID: " + pickTask.getBinId());
        holder.productId.setText("Product ID: " + pickTask.getProductId());
        holder.quantity.setText("To Pick: " + pickTask.getQuantity());
        holder.scannedQuantity.setText("Scanned: " + pickTask.getScannedQuantity());

        holder.scanButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onScanButtonClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pickTasks.size();
    }

    static class PickTaskViewHolder extends RecyclerView.ViewHolder {
        TextView binId;
        TextView productId;
        TextView quantity;
        TextView scannedQuantity;
        Button scanButton;

        public PickTaskViewHolder(@NonNull View itemView) {
            super(itemView);
            binId = itemView.findViewById(R.id.tv_bin_id);
            productId = itemView.findViewById(R.id.tv_product_id);
            quantity = itemView.findViewById(R.id.tv_quantity);
            scannedQuantity = itemView.findViewById(R.id.tv_scanned_quantity);
            scanButton = itemView.findViewById(R.id.btn_scan_rfid);
        }
    }
} 