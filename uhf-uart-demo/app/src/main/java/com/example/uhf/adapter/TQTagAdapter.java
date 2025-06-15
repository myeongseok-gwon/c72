package com.example.uhf.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.uhf.R;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.List;

/**
 * TQ 태그 목록 어댑터
 */
public class TQTagAdapter extends BaseAdapter {
    
    private Context context;
    private List<UHFTAGInfo> tagList;
    private LayoutInflater inflater;
    private int selectedPosition = -1;
    
    public TQTagAdapter(Context context, List<UHFTAGInfo> tagList) {
        this.context = context;
        this.tagList = tagList;
        this.inflater = LayoutInflater.from(context);
    }
    
    @Override
    public int getCount() {
        return tagList.size();
    }
    
    @Override
    public Object getItem(int position) {
        return tagList.get(position);
    }
    
    @Override
    public long getItemId(int position) {
        return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.tq_tag_item, parent, false);
            holder = new ViewHolder();
            holder.tvEpc = convertView.findViewById(R.id.tvEpc);
            holder.tvCount = convertView.findViewById(R.id.tvCount);
            holder.tvRssi = convertView.findViewById(R.id.tvRssi);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        UHFTAGInfo tagInfo = tagList.get(position);
        
        // EPC 표시
        holder.tvEpc.setText(tagInfo.getEPC());
        
        // 카운트 표시
        holder.tvCount.setText(String.valueOf(tagInfo.getCount()));
        
        // RSSI 표시
        holder.tvRssi.setText(tagInfo.getRssi() + " dBm");
        
        // 선택된 아이템 하이라이트
        if (position == selectedPosition) {
            convertView.setBackgroundColor(Color.parseColor("#e3f2fd"));
        } else {
            convertView.setBackgroundColor(Color.TRANSPARENT);
        }
        
        return convertView;
    }
    
    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }
    
    private static class ViewHolder {
        TextView tvEpc;
        TextView tvCount;
        TextView tvRssi;
    }
}