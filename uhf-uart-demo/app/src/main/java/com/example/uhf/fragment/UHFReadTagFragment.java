package com.example.uhf.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.iot.AwsIotManager;
import com.example.uhf.tools.CheckUtils;
import com.example.uhf.tools.NumberTool;
import com.example.uhf.tools.StringUtils;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UHFReadTagFragment extends KeyDwonFragment {
    private static final String TAG = "UHFReadTagFragment";
    
    // AWS IoT 관련
    private AwsIotManager awsIotManager;
    private TextView tvIotStatus;
    private Button btConnectIot;
    private CheckBox cbAutoPublish;
    
    // 기존 코드
    private int inventoryFlag = 1;
    MyAdapter adapter;
    Button BtClear;
    TextView tvTime, tv_count, tv_total;
    RadioGroup RgInventory;
    RadioButton RbInventorySingle;
    RadioButton RbInventoryLoop;

    private CheckBox cbFilter, cbPhase;
    private ViewGroup layout_filter;
    private CheckBox cbEPC_Tam;

    long maxRunTime = 36000000L;//毫秒
    EditText etTime;
    Button BtInventory;
    public static ListView LvTags;
    public UHFMainActivity mContext;
    private long startTime = SystemClock.elapsedRealtime();
    private int total;

    private final int MSG_STOP = 3;
    private final int MSG_IOT_STATUS_UPDATE = 4;
    
    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                UHFTAGInfo info = (UHFTAGInfo) msg.obj;
                addDataToList(info);
                
                // AWS IoT로 이벤트 발송 (연속 스캔 모드에서도 작동)
                publishRfidEventInLoop(info);
                
            } else if (msg.what == 2) {
                if (mContext.loopFlag) {
                    handler.sendEmptyMessageDelayed(2, 10);
                    setTotalTime();
                } else {
                    handler.removeMessages(2);
                }
            } else if (msg.what == MSG_STOP) {
                stopInventory();
            } else if (msg.what == MSG_IOT_STATUS_UPDATE) {
                updateIotStatusDisplay();
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(TAG, "UHFReadTagFragment.onCreateView");
        if (playSoundThread == null) {
            playSoundThread = new PlaySoundThread();
            playSoundThread.start();
        }
        return inflater.inflate(R.layout.uhf_readtag_fragment, container, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mContext.mReader.setInventoryCallback(null);
        
        // AWS IoT 연결 해제
        if (awsIotManager != null) {
            awsIotManager.disconnect();
        }
        
        Log.i(TAG, "onDestroyView");
        if (playSoundThread != null) {
            playSoundThread.stopPlay();
            playSoundThread = null;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "UHFReadTagFragment.onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;

        // 기존 UI 요소들 초기화
        initializeUIElements();
        
        // AWS IoT UI 요소들 초기화
        initializeAwsIotUI();
        
        // AWS IoT 매니저 초기화
        initializeAwsIot();

        initFilter(getView());
        initEPCTamperAlarm(getView());
        
        tv_count.setText(mContext.tagList.size() + "");
        tv_total.setText(total + "");
        Log.i(TAG, "UHFReadTagFragment.EtCountOfTags=" + tv_count.getText());
    }
    
    private void initializeUIElements() {
        BtClear = (Button) getView().findViewById(R.id.BtClear);
        tvTime = (TextView) getView().findViewById(R.id.tvTime);
        tvTime.setText("0s");
        tv_count = (TextView) getView().findViewById(R.id.tv_count);
        tv_total = (TextView) getView().findViewById(R.id.tv_total);
        RgInventory = (RadioGroup) getView().findViewById(R.id.RgInventory);
        RbInventorySingle = (RadioButton) getView().findViewById(R.id.RbInventorySingle);
        RbInventoryLoop = (RadioButton) getView().findViewById(R.id.RbInventoryLoop);
        etTime = (EditText) getView().findViewById(R.id.etTime);
        BtInventory = (Button) getView().findViewById(R.id.BtInventory);
        cbPhase = (CheckBox) getView().findViewById(R.id.cbPhase);

        LvTags = (ListView) getView().findViewById(R.id.LvTags);
        adapter = new MyAdapter(mContext);
        LvTags.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                adapter.setSelectItem(position);
                adapter.notifyDataSetInvalidated();
            }
        });
        LvTags.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                ClipboardManager clipboard = (ClipboardManager) view.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("label", mContext.tagList.get(position).getEPC());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(view.getContext(), R.string.msg_copy_clipboard, Toast.LENGTH_SHORT).show();
                return false;
            }
        });
        LvTags.setAdapter(adapter);
        BtClear.setOnClickListener(new BtClearClickListener());
        RgInventory.setOnCheckedChangeListener(new RgInventoryCheckedListener());
        BtInventory.setOnClickListener(new BtInventoryClickListener());
    }
    
    private void initializeAwsIotUI() {
        // AWS IoT 상태 표시 텍스트뷰 (기존 레이아웃에 추가해야 함)
        // tvIotStatus = (TextView) getView().findViewById(R.id.tvIotStatus);
        // btConnectIot = (Button) getView().findViewById(R.id.btConnectIot);
        // cbAutoPublish = (CheckBox) getView().findViewById(R.id.cbAutoPublish);
        
        // 임시로 토스트로 상태 표시 (실제로는 레이아웃에 UI 요소 추가 필요)
        if (tvIotStatus != null) {
            tvIotStatus.setText("IoT 상태: 연결 해제됨");
            tvIotStatus.setTextColor(Color.RED);
        }
        
        if (btConnectIot != null) {
            btConnectIot.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    connectToAwsIot();
                }
            });
        }
        
        // cbAutoPublish가 null인 경우 기본값으로 자동 발송 활성화
        if (cbAutoPublish != null) {
            cbAutoPublish.setChecked(true);
        }
        
        Log.i(TAG, "AWS IoT UI 초기화 완료 - AutoPublish: " + (cbAutoPublish != null ? cbAutoPublish.isChecked() : "true(default)"));
    }
    
    private void initializeAwsIot() {
        awsIotManager = new AwsIotManager(mContext);
        // 자동으로 AWS IoT에 연결 시도
        connectToAwsIot();
    }
    
    private void connectToAwsIot() {
        Toast.makeText(mContext, "AWS IoT Core 연결 중...", Toast.LENGTH_SHORT).show();
        
        awsIotManager.connectWithCertificate(new AwsIotManager.ConnectionCallback() {
            @Override
            public void onConnectionSuccess() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, "AWS IoT Core 연결 성공!", Toast.LENGTH_SHORT).show();
                        updateIotStatusDisplay();
                    }
                });
            }

            @Override
            public void onConnectionFailure(Exception exception) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, "AWS IoT Core 연결 실패: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                        updateIotStatusDisplay();
                    }
                });
            }
        });
    }
    
    private void updateIotStatusDisplay() {
        if (tvIotStatus != null) {
            if (awsIotManager != null && awsIotManager.isConnected()) {
                tvIotStatus.setText("IoT 상태: 연결됨");
                tvIotStatus.setTextColor(Color.GREEN);
            } else {
                tvIotStatus.setText("IoT 상태: 연결 해제됨");
                tvIotStatus.setTextColor(Color.RED);
            }
        }
    }
    
    private void publishRfidEvent(UHFTAGInfo info) {
        if (awsIotManager == null || !awsIotManager.isConnected()) {
            Log.w(TAG, "AWS IoT가 연결되지 않아 이벤트를 발송할 수 없습니다.");
            return;
        }
        
        // 타임스탬프 생성
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        String timestamp = sdf.format(new Date());
        
        // RFID 이벤트 발송
        awsIotManager.publishRfidScanEvent(
            info.getEPC(),
            info.getRssi(),
            timestamp,
            new AwsIotManager.PublishCallback() {
                @Override
                public void onPublishSuccess() {
                    Log.i(TAG, "RFID 이벤트 발송 성공: " + info.getEPC());
                }

                @Override
                public void onPublishFailure(Exception exception) {
                    Log.e(TAG, "RFID 이벤트 발송 실패: " + exception.getMessage());
                }
            }
        );
    }
    
    /**
     * 연속 스캔 모드에서 AWS IoT 이벤트 발송
     * Handler 스레드에서 호출되므로 UI 스레드 체크 불필요
     */
    private void publishRfidEventInLoop(UHFTAGInfo info) {
        // 연결 상태 체크
        if (awsIotManager == null || !awsIotManager.isConnected()) {
            Log.w(TAG, "Loop 모드: AWS IoT 연결되지 않음");
            return;
        }
        
        // 자동 발송 활성화 체크 (cbAutoPublish가 null이면 기본적으로 활성화)
        boolean autoPublishEnabled = (cbAutoPublish == null) || cbAutoPublish.isChecked();
        if (!autoPublishEnabled) {
            Log.d(TAG, "Loop 모드: 자동 발송 비활성화됨");
            return;
        }
        
        Log.i(TAG, "Loop 모드: RFID 이벤트 발송 시도 - EPC: " + info.getEPC());
        
        // 타임스탬프 생성
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        String timestamp = sdf.format(new Date());
        
        // RFID 이벤트 발송
        awsIotManager.publishRfidScanEvent(
            info.getEPC(),
            info.getRssi(),
            timestamp,
            new AwsIotManager.PublishCallback() {
                @Override
                public void onPublishSuccess() {
                    Log.i(TAG, "✅ Loop 모드 RFID 이벤트 발송 성공: " + info.getEPC());
                }

                @Override
                public void onPublishFailure(Exception exception) {
                    Log.e(TAG, "❌ Loop 모드 RFID 이벤트 발송 실패: " + exception.getMessage());
                }
            }
        );
    }

    private Button btnSetFilter;

    private void initFilter(View view) {
        layout_filter = (ViewGroup) view.findViewById(R.id.layout_filter);
        layout_filter.setVisibility(View.GONE);
        cbFilter = (CheckBox) view.findViewById(R.id.cbFilter);
        cbFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                layout_filter.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        final EditText etOffset = (EditText) view.findViewById(R.id.etPtr);
        final EditText etLen = (EditText) view.findViewById(R.id.etLen);
        final EditText etData = (EditText) view.findViewById(R.id.etData);
        final RadioButton rbEPC = (RadioButton) view.findViewById(R.id.rbEPC);
        final RadioButton rbTID = (RadioButton) view.findViewById(R.id.rbTID);
        final RadioButton rbUser = (RadioButton) view.findViewById(R.id.rbUser);

        etData.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                etLen.setText(String.valueOf(etData.getText().toString().trim().length() * 4));
            }
        });

        btnSetFilter = (Button) view.findViewById(R.id.btSet);
        btnSetFilter.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                int filterBank = RFIDWithUHFUART.Bank_EPC;
                if (rbEPC.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_EPC;
                } else if (rbTID.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_TID;
                } else if (rbUser.isChecked()) {
                    filterBank = RFIDWithUHFUART.Bank_USER;
                }
                etLen.getText().toString();
                if (etLen.getText().toString().isEmpty()) {
                    mContext.showToast("데이터 길이를 입력하세요");
                    return;
                }
                etOffset.getText().toString();
                if (etOffset.getText().toString().isEmpty()) {
                    mContext.showToast("시작 주소를 입력하세요");
                    return;
                }
                int ptr = StringUtils.toInt(etOffset.getText().toString(), 0);
                int len = StringUtils.toInt(etLen.getText().toString(), 0);
                String data = etData.getText().toString().trim();
                if (len > 0) {
                    String rex = "[\\da-fA-F]*";
                    if (data.isEmpty() || !data.matches(rex)) {
                        mContext.showToast(getString(R.string.uhf_msg_filter_data_must_hex));
                        return;
                    }

                    if (mContext.mReader.setFilter(filterBank, ptr, len, data)) {
                        mContext.showToast(R.string.uhf_msg_set_filter_succ);
                    } else {
                        mContext.showToast(R.string.uhf_msg_set_filter_fail);
                    }
                } else {
                    String dataStr = "";
                    if (mContext.mReader.setFilter(RFIDWithUHFUART.Bank_EPC, 0, 0, dataStr)
                            && mContext.mReader.setFilter(RFIDWithUHFUART.Bank_TID, 0, 0, dataStr)
                            && mContext.mReader.setFilter(RFIDWithUHFUART.Bank_USER, 0, 0, dataStr)) {
                        mContext.showToast(R.string.msg_disable_succ);
                    } else {
                        mContext.showToast(R.string.msg_disable_fail);
                    }
                }
                cbFilter.setChecked(false);
            }
        });
        
        CheckBox cb_filter = (CheckBox) view.findViewById(R.id.cb_filter);
        rbEPC.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbEPC.isChecked()) {
                    etOffset.setText("32");
                }
            }
        });
        rbTID.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbTID.isChecked()) {
                    etOffset.setText("0");
                }
            }
        });
        rbUser.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rbUser.isChecked()) {
                    etOffset.setText("0");
                }
            }
        });
    }

    private void initEPCTamperAlarm(View view) {
        cbEPC_Tam = (CheckBox) view.findViewById(R.id.cbEPC_Tam);
        cbEPC_Tam.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    //mContext.mReader.setEPCAndTamperAlarmMode();
                } else {
                    mContext.mReader.setEPCMode();
                }
            }
        });
    }

    @Override
    public void onPause() {
        Log.i(TAG, "UHFReadTagFragment.onPause");
        super.onPause();
        stopInventory();
    }

    /**
     * 리스트에 데이터 추가
     */
    private void addDataToList(UHFTAGInfo info) {
        String epc = info.getEPC();
        if (StringUtils.isNotEmpty(epc)) {
            boolean[] exists = new boolean[1];
            int insertIndex = CheckUtils.getInsertIndex(mContext.tagList, info, exists);
            if (exists[0]) {
                info.setCount(mContext.tagList.get(insertIndex).getCount() + 1);
                mContext.tagList.set(insertIndex, info);
            } else {
                mContext.tagList.add(insertIndex, info);
                tv_count.setText(String.valueOf(adapter.getCount()));
            }
            tv_total.setText(String.valueOf(++total));
            adapter.notifyDataSetChanged();
        }
    }

    public class BtClearClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            clearData();
            mContext.selectIndex = -1;
        }
    }

    private void clearData() {
        tv_count.setText("0");
        tv_total.setText("0");
        tvTime.setText("0s");
        total = 0;
        mContext.tagList.clear();
        adapter.notifyDataSetChanged();
    }

    public class RgInventoryCheckedListener implements OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (checkedId == RbInventorySingle.getId()) {
                inventoryFlag = 0;
            } else if (checkedId == RbInventoryLoop.getId()) {
                inventoryFlag = 1;
            }
        }
    }

    public class BtInventoryClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            readTag();
        }
    }

    private void readTag() {
        cbFilter.setChecked(false);
        if (BtInventory.getText().equals(mContext.getString(R.string.btInventory))) {
            switch (inventoryFlag) {
                case 0:// 단일 스캔
                    startTime = SystemClock.elapsedRealtime();
                    UHFTAGInfo uhftagInfo = mContext.mReader.inventorySingleTag();
                    if (uhftagInfo != null) {
                        addDataToList(uhftagInfo);
                        setTotalTime();
                        mContext.playSound(1);
                        
                        // AWS IoT로 단일 스캔 이벤트 발송
                        if (cbAutoPublish == null || cbAutoPublish.isChecked()) {
                            publishRfidEvent(uhftagInfo);
                        }
                    } else {
                        mContext.showToast(R.string.uhf_msg_inventory_fail);
                    }
                    break;
                case 1:// 연속 스캔
                    mContext.mReader.setInventoryCallback(new IUHFInventoryCallback() {
                        @Override
                        public void callback(UHFTAGInfo uhftagInfo) {
                            Message msg = handler.obtainMessage();
                            msg.obj = uhftagInfo;
                            msg.what = 1;
                            handler.sendMessage(msg);
                            playSoundThread.play();
                        }
                    });
                    playSoundThread.cleanData();

                    InventoryParameter inventoryParameter = new InventoryParameter();
                    inventoryParameter.setResultData(new InventoryParameter.ResultData().setNeedPhase(cbPhase.isChecked()));
                    if (mContext.mReader.startInventoryTag(inventoryParameter)) {
                        String time = etTime.getText().toString();
                        if (time.length() > 0 && time.startsWith(".")) {
                            etTime.setText("");
                            time = "";
                        }
                        if (!time.isEmpty()) {
                            maxRunTime = (int) (Float.parseFloat(time) * 1000);
                            clearData();
                        } else {
                            maxRunTime = Long.parseLong(etTime.getHint().toString()) * 1000;
                        }
                        handler.removeMessages(MSG_STOP);
                        handler.sendEmptyMessageDelayed(MSG_STOP, maxRunTime);
                        Log.i(TAG, "maxRunTime=" + maxRunTime);

                        BtInventory.setText(mContext.getString(R.string.title_stop_Inventory));
                        mContext.loopFlag = true;
                        setViewEnabled(false);
                        startTime = SystemClock.elapsedRealtime();
                        handler.sendEmptyMessageDelayed(2, 10);
                    } else {
                        stopInventory();
                        mContext.showToast(R.string.uhf_msg_inventory_open_fail);
                    }
                    break;
                default:
                    break;
            }
        } else {
            stopInventory();
            setTotalTime();
        }
    }

    private void setTotalTime() {
        float useTime = (SystemClock.elapsedRealtime() - startTime) / 1000.0F;
        tvTime.setText(NumberTool.getPointDouble(1, useTime) + "s");
    }

    private void setViewEnabled(boolean enabled) {
        RbInventorySingle.setEnabled(enabled);
        RbInventoryLoop.setEnabled(enabled);
        cbFilter.setEnabled(enabled);
        btnSetFilter.setEnabled(enabled);
        cbEPC_Tam.setEnabled(enabled);
        cbPhase.setEnabled(enabled);
    }

    private void stopInventory() {
        handler.removeMessages(MSG_STOP);
        if (mContext.loopFlag) {
            mContext.loopFlag = false;
            setViewEnabled(true);
            if (mContext.mReader.stopInventory()) {
                BtInventory.setText(mContext.getString(R.string.btInventory));
            } else {
                mContext.showToast(R.string.uhf_msg_inventory_stop_fail);
            }
        }
    }

    private String mergeTidEpc(UHFTAGInfo uhftagInfo) {
        String data = "";
        if (uhftagInfo.getReserved() != null && !uhftagInfo.getReserved().isEmpty()) {
            data += "RESERVED:" + uhftagInfo.getReserved();
            data += "\nEPC:" + uhftagInfo.getEPC();
        } else {
            data += TextUtils.isEmpty(uhftagInfo.getTid()) ? uhftagInfo.getEPC() : "EPC:" + uhftagInfo.getEPC();
        }
        if (!TextUtils.isEmpty(uhftagInfo.getTid())
                && !uhftagInfo.getTid().equals("0000000000000000")
                && !uhftagInfo.getTid().equals("000000000000000000000000")
        ) {
            data += "\nTID:" + uhftagInfo.getTid();
        }
        if (uhftagInfo.getUser() != null && uhftagInfo.getUser().length() > 0) {
            data += "\nUSER:" + uhftagInfo.getUser();
        }
        return data;
    }

    @Override
    public void myOnKeyDwon() {
        readTag();
    }

    // 어댑터 및 기타 클래스들
    public final class ViewHolder {
        public TextView tvTag;
        public TextView tvTagCount;
        public TextView tvTagRssi;
        public TextView tvPhase;
    }

    public class MyAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        public MyAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return mContext.tagList.size();
        }

        public Object getItem(int arg0) {
            return mContext.tagList.get(arg0);
        }

        public long getItemId(int arg0) {
            return arg0;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.listtag_items, null);
                holder.tvTag = (TextView) convertView.findViewById(R.id.TvTagUii);
                holder.tvTagCount = (TextView) convertView.findViewById(R.id.TvTagCount);
                holder.tvTagRssi = (TextView) convertView.findViewById(R.id.TvTagRssi);
                holder.tvPhase = (TextView) convertView.findViewById(R.id.TvPhase);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            UHFTAGInfo uhftagInfo = mContext.tagList.get(position);
            holder.tvTag.setText(mergeTidEpc(uhftagInfo));
            holder.tvTagCount.setText(String.valueOf(uhftagInfo.getCount()));
            holder.tvTagRssi.setText(uhftagInfo.getRssi());
            holder.tvPhase.setText(String.valueOf(uhftagInfo.getPhase()));

            if (position == mContext.selectIndex) {
                convertView.setBackgroundColor(mContext.getResources().getColor(R.color.lfile_colorPrimary));
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            return convertView;
        }

        public void setSelectItem(int select) {
            if (mContext.selectIndex == select) {
                mContext.selectIndex = -1;
            } else {
                mContext.selectIndex = select;
            }
        }
    }

    // 사운드 스레드
    private Object objectLock = new Object();
    PlaySoundThread playSoundThread = null;

    private class PlaySoundThread extends Thread {
        private boolean isStop = false;
        ConcurrentLinkedQueue queue = new ConcurrentLinkedQueue();
        long count = 0;
        long consumption = 0;

        @Override
        public void run() {
            while (!isStop) {
                if (queue.isEmpty()) {
                    synchronized (objectLock) {
                        try {
                            objectLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (mContext.loopFlag) {
                    mContext.playSound(1);
                    queue.poll();
                    consumption++;
                }
                if (count - consumption > 50) {
                    for (int k = 0; k < 25; k++) {
                        queue.poll();
                    }
                    consumption += 25;
                }
            }
        }

        public void play() {
            queue.offer(1);
            synchronized (objectLock) {
                objectLock.notifyAll();
                count++;
            }
        }

        public void cleanData() {
            count = 0;
            consumption = 0;
            queue.clear();
        }

        public void stopPlay() {
            isStop = true;
            count = 0;
            consumption = 0;
            queue.clear();
            synchronized (objectLock) {
                objectLock.notifyAll();
            }
        }
    }
}