package com.example.uhf.fragment;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.tools.StringUtils;
import com.example.uhf.tools.UIHelper;

import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.utility.StringUtility;

public class BlockWriteFragment extends KeyDwonFragment implements View.OnClickListener {

    private static final String TAG = "UHFWriteFragment";

    private UHFMainActivity mContext;


    Spinner SpinnerBank_Write;
    EditText EtPtr_Write;
    EditText EtLen_Write;
    EditText EtData_Write;
    EditText EtAccessPwd_Write;
    EditText etLen_filter_wt;

    Button BtWrite;

    CheckBox cb_filter_wt;
    EditText etPtr_filter_wt;
    EditText etData_filter_wt;
    RadioButton rbEPC_filter_wt;
    RadioButton rbTID_filter_wt;
    RadioButton rbUser_filter_wt;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_block_write, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        SpinnerBank_Write = (Spinner) getView().findViewById(R.id.SpinnerBank_Write);
        EtPtr_Write = (EditText) getView().findViewById(R.id.EtPtr_Write);
        EtLen_Write = (EditText) getView().findViewById(R.id.EtLen_Write);
        EtData_Write = (EditText) getView().findViewById(R.id.EtData_Write);
        EtAccessPwd_Write = (EditText) getView().findViewById(R.id.EtAccessPwd_Write);
        etLen_filter_wt = (EditText) getView().findViewById(R.id.etLen_filter_wt);
        BtWrite = (Button) getView().findViewById(R.id.BtWrite);


        cb_filter_wt = (CheckBox) getView().findViewById(R.id.cb_filter_wt);
        etPtr_filter_wt = (EditText) getView().findViewById(R.id.etPtr_filter_wt);
        etData_filter_wt = (EditText) getView().findViewById(R.id.etData_filter_wt);
        rbEPC_filter_wt = (RadioButton) getView().findViewById(R.id.rbEPC_filter_wt);
        rbTID_filter_wt = (RadioButton) getView().findViewById(R.id.rbTID_filter_wt);
        rbUser_filter_wt = (RadioButton) getView().findViewById(R.id.rbUser_filter_wt);

        rbEPC_filter_wt.setOnClickListener(this);
        rbTID_filter_wt.setOnClickListener(this);
        rbUser_filter_wt.setOnClickListener(this);
        BtWrite.setOnClickListener(this);


        cb_filter_wt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    String data = etData_filter_wt.getText().toString().trim();
                    String rex = "[\\da-fA-F]*"; //匹配正则表达式，数据为十六进制格式
                    if (data == null || data.isEmpty() || !data.matches(rex)) {
                        UIHelper.ToastMessage(mContext,  getString(R.string.uhf_msg_filter_data_must_hex));
                        cb_filter_wt.setChecked(false);
                        return;
                    }
                }
            }
        });
        SpinnerBank_Write.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String element = adapterView.getItemAtPosition(i).toString();// 得到spanner的值
                if (element.equals("EPC")) {
                    EtPtr_Write.setText("2");
                } else {
                    EtPtr_Write.setText("0");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.rbEPC_filter_wt:
                etPtr_filter_wt.setText("32");
                break;
            case R.id.rbTID_filter_wt:
                etPtr_filter_wt.setText("0");
                break;
            case R.id.rbUser_filter_wt:
                etPtr_filter_wt.setText("0");
                break;
            case R.id.BtWrite:
                write();
                break;
        }
    }

    private void write() {
        String strPtr = EtPtr_Write.getText().toString().trim();
        if (StringUtils.isEmpty(strPtr)) {
            UIHelper.ToastMessage(mContext, R.string.uhf_msg_addr_not_null);
            return;
        } else if (!StringUtility.isDecimal(strPtr)) {
            UIHelper.ToastMessage(mContext,
                    R.string.uhf_msg_addr_must_decimal);
            return;
        }

        String strPWD = EtAccessPwd_Write.getText().toString().trim();// 访问密码
        if (StringUtils.isNotEmpty(strPWD)) {
            if (strPWD.length() != 8) {
                UIHelper.ToastMessage(mContext,
                        R.string.uhf_msg_addr_must_len8);
                return;
            } else if (!mContext.vailHexInput(strPWD)) {
                UIHelper.ToastMessage(mContext,
                        R.string.rfid_mgs_error_nohex);
                return;
            }
        } else {
            strPWD = "00000000";
        }

        String strData = EtData_Write.getText().toString().trim();// 要写入的内容
        if (StringUtils.isEmpty(strData)) {
            UIHelper.ToastMessage(mContext,
                    R.string.uhf_msg_write_must_not_null);
            return;
        } else if (!mContext.vailHexInput(strData)) {
            UIHelper.ToastMessage(mContext, R.string.rfid_mgs_error_nohex);
            return;
        }

        // 多字单次
        String cntStr = EtLen_Write.getText().toString().trim();
        if (StringUtils.isEmpty(cntStr)) {
            UIHelper.ToastMessage(mContext, R.string.uhf_msg_len_not_null);
            return;
        } else if (!StringUtility.isDecimal(cntStr)) {
            UIHelper.ToastMessage(mContext,
                    R.string.uhf_msg_len_must_decimal);
            return;
        }

        if ((strData.length()) % 4 != 0) {
            UIHelper.ToastMessage(mContext,
                    R.string.uhf_msg_write_must_len4x);

            return;
        } else if (!mContext.vailHexInput(strData)) {
            UIHelper.ToastMessage(mContext, R.string.rfid_mgs_error_nohex);
            return;
        }


        boolean result = false;
        int Bank = SpinnerBank_Write.getSelectedItemPosition();

        if (cb_filter_wt.isChecked())// 指定标签
        {
            if (etPtr_filter_wt.getText().toString() == null || etPtr_filter_wt.getText().toString().isEmpty()) {
                etPtr_filter_wt.setText("0");
            }
            if (etLen_filter_wt.getText().toString() == null || etLen_filter_wt.getText().toString().isEmpty()) {
                UIHelper.ToastMessage(mContext, getString(R.string.uhf_msg_filter_len_not_null));
                return;
            }

            int filterPtr = Integer.parseInt(etPtr_filter_wt.getText().toString());
            String filterData = etData_filter_wt.getText().toString();
            int filterCnt = Integer.parseInt(etLen_filter_wt.getText().toString());
            int filterBank = RFIDWithUHFUART.Bank_EPC;
            if (rbEPC_filter_wt.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_EPC;
            } else if (rbTID_filter_wt.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_TID;
            } else if (rbUser_filter_wt.isChecked()) {
                filterBank = RFIDWithUHFUART.Bank_USER;
            }
            boolean r = mContext.mReader.blockWriteData(strPWD,
                    filterBank,
                    filterPtr,
                    filterCnt,
                    filterData,
                    Bank,
                    Integer.parseInt(strPtr),
                    Integer.parseInt(cntStr),
                    strData
            );
            if (r) {
                result = true;
            } else {
                result = false;
            }

        } else {
            boolean r = mContext.mReader.blockWriteData(strPWD,
                    RFIDWithUHFUART.Bank_EPC,
                    0,
                    0,
                    "00",
                    Bank,
                    Integer.parseInt(strPtr),
                    Integer.parseInt(cntStr),
                    strData
            );
            if (r) {
                result = true;
            } else {
                result = false;
            }
        }
        if (!result) {
            mContext.playSound(2);
        } else {
            mContext.playSound(1);
        }
    }


}
