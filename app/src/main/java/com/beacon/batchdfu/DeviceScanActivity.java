/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.beacon.batchdfu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.core.app.ActivityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.kkmcn.kbeaconlib2.KBConnPara;
import com.kkmcn.kbeaconlib2.KBConnState;
import com.kkmcn.kbeaconlib2.KBConnectionEvent;
import com.kkmcn.kbeaconlib2.KBeacon;
import com.kkmcn.kbeaconlib2.KBeaconsMgr;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends AppBaseActivity implements View.OnClickListener, AdapterView.OnItemClickListener,
        LeDeviceListAdapter.ListDataSource, KBeaconsMgr.KBeaconMgrDelegate {
    private final static String TAG = "Beacon.ScanAct";//DeviceScanActivity.class.getSimpleName();

    private final static int MSG_CHK_TIMER_MY = 203;
    private final static int MSG_REFERASH_VIEW = 204;
    private final static int MSG_REFERASH_ONE_ITEM_VIEW = 205;
    private final static int MSG_PERIOD_CHECK_CONNECT = 210;
    public static final int ERR_CONN_TIMEOUT = 401;
    public static final int ERR_CONN_FAIL = 402;
    public static final int ERR_CONN_EXCEPTION_DISCONNECTED = 403;
    public static final int ERR_CONN_AUTH_FAIL = 404;
    public static final int ERR_CONN_SUCCESS = 405;
    private final static String LOG_TAG = "DeviceScan";

    private long mLastScanTick = 0;
    private KBeaconsMgr mBeaconMgr;
    private ListView mListView;
    private EditText mEditFltDevName;
    private LeDeviceListAdapter mDevListAdapter;
    public Handler mHandler;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressDialog mProgressDialog;
    private String mCurrentConnectingDevMac;
    private int mScanFailedContinueNum = 0;

    private Button mBtnFilterTotal, mBtnRmvAllFilter, mBtnFilterArrow, mBtnRmvNameFilter;
    private TextView mTxtViewRssi;
    private SeekBar mSeekBarRssi;
    private int mRssiFilterValue;
    private LinearLayout mLayoutFilterName, mLayoutFilterRssi;

    private final static int MAX_ERROR_SCAN_NUMBER = 2;
    private static final long MIN_SCAN_INTERVAL = 15 * 1000;
    private static final long CHK_TIMER_PERIOD = 4 * 1010;

    private boolean mPendingScanning = false;
    private boolean mManualStartScanning = false;

    private HashMap<String, KBeacon> mBeaconsDictory;
    private ArrayList<KBeacon> mBeaconsArray;
    KBDfuPreference mDfuCfg = null;
    private ActivityResultLauncher<Intent> mProvisionLauncher;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new MsgHandler();
        mDfuCfg = KBDfuPreference.shareInstance(this);

        setContentView(R.layout.main_activity);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        setTitle(R.string.beacon_list_view);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        mBeaconsDictory = new HashMap<>(50);
        mBeaconsArray = new ArrayList<>(50);

        //init ble manager
        mBeaconMgr = KBeaconsMgr.sharedBeaconManager(this);
        if (mBeaconMgr == null) {
            Log.e(TAG, "Unable to initialize Bluetooth");
            finish();
        }
        mBeaconMgr.delegate = this;
        mBeaconMgr.setScanMode(KBeaconsMgr.SCAN_MODE_LOW_LATENCY);
        mListView = findViewById(R.id.listview);
        mDevListAdapter = new LeDeviceListAdapter(getApplicationContext(), this);
        mListView.setAdapter(mDevListAdapter);
        mListView.setOnItemClickListener(this);

        //total filter information
        mBtnFilterTotal =  findViewById(R.id.btnFilterInfo);
        mBtnFilterTotal.setOnClickListener(this);
        mBtnRmvAllFilter = findViewById(R.id.btnRemoveAllFilter);
        mBtnRmvAllFilter.setOnClickListener(this);
        mBtnRmvAllFilter.setVisibility(View.GONE);
        mBtnFilterArrow = findViewById(R.id.imageButtonArrow);
        mBtnFilterArrow.setOnClickListener(this);
        mBtnFilterArrow.setTag(0);

        //filter layout
        mLayoutFilterName = findViewById(R.id.layFilterName);
        mLayoutFilterName.setVisibility(View.GONE);


        mLayoutFilterRssi = findViewById(R.id.layRssiFilter);
        mLayoutFilterRssi.setVisibility(View.GONE);

        //remove action
        findViewById(R.id.btmRemoveFilterName).setOnClickListener(this);
        mTxtViewRssi = findViewById(R.id.txtViewRssiValue);
        mEditFltDevName = findViewById(R.id.editFilterName);
        mEditFltDevName.setText(mDfuCfg.getFilterName());
        mEditFltDevName.addTextChangedListener(new EditChangedListener());
        mBtnRmvNameFilter = findViewById(R.id.btmRemoveFilterName);
        mSeekBarRssi = findViewById(R.id.seekBarRssiFilter);

        //init filter
        mRssiFilterValue = mDfuCfg.getFilterRssi();
        if (mRssiFilterValue != -100) {
            mSeekBarRssi.setProgress(mRssiFilterValue + 100);
            String strRssiValue = mRssiFilterValue + getString(R.string.BEACON_RSSI_UINT);
            mTxtViewRssi.setText(strRssiValue);

            enableFilterSetting();
        }

        mSeekBarRssi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mRssiFilterValue = progress - 100;
                String strRssiValue = mRssiFilterValue + getString(R.string.BEACON_RSSI_UINT);
                mTxtViewRssi.setText(strRssiValue);
                mDfuCfg.setFilterRssi(mRssiFilterValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        swipeRefreshLayout =  findViewById(R.id.swipe_container);
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_light, android.R.color.holo_red_light, android.R.color.holo_orange_light, android.R.color.holo_green_light);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // TODO Auto-generated method stub
            new Handler().postDelayed(() -> {
                // TODO Auto-generated method stub
                swipeRefreshLayout.setRefreshing(false);
                if (System.currentTimeMillis() - mLastScanTick > MIN_SCAN_INTERVAL) {
                    if (mScanFailedContinueNum >= MAX_ERROR_SCAN_NUMBER) {
                        mScanFailedContinueNum = 0;
                        new AlertDialog.Builder(DeviceScanActivity.this, R.style.AlertDialogStyle)
                                .setTitle(R.string.common_error_title)
                                .setMessage(R.string.bluetooth_error_need_reboot)
                                .setPositiveButton(R.string.Dialog_OK, null)
                                .show();
                    } else {
                        clearAllData();
                        mDevListAdapter.notifyDataSetChanged();
                    }
                }
            }, 500);
        });

        checkBluetoothPermitAllowed();

        mProvisionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (data != null) {
                        CfgDFURecord dfuRecord = new CfgDFURecord();
                        dfuRecord.macAddress = data.getStringExtra(CfgBeaconDFUActivity.DEVICE_MAC_ADDRESS);
                        dfuRecord.model = data.getStringExtra(CfgBeaconDFUActivity.DEVICE_MODEL);
                        dfuRecord.dfuVer = data.getStringExtra(CfgBeaconDFUActivity.DFU_VERSION);
                        dfuRecord.ver = data.getStringExtra(CfgBeaconDFUActivity.CURR_VERSION);
                        dfuRecord.result = result.getResultCode();
                        mDfuCfg.setDfuResult(dfuRecord);
                    }
                }
        );

        mHandler.sendEmptyMessageDelayed(MSG_CHK_TIMER_MY, CHK_TIMER_PERIOD);
    }


    private final KBeacon.ConnStateDelegate mBeaconConnEvtCallback = new KBeacon.ConnStateDelegate() {
        @Override
        public void onConnStateChange(KBeacon beacon, KBConnState state, int nReason) {
            final String strMacAddr = beacon.getMac();
            if (mCurrentConnectingDevMac == null || !mCurrentConnectingDevMac.equals(strMacAddr)) {
                return;
            }

            //connect timeout
            if (state == KBConnState.Connected) {
                handleConnCompleteNtf(beacon, ERR_CONN_SUCCESS);
            } else if (state == KBConnState.Disconnected) {

                //save disconnect time
                mDfuCfg.setDisconnectingTime(mCurrentConnectingDevMac, System.currentTimeMillis());

                if (nReason == KBConnectionEvent.ConnAuthFail) {
                    handleConnCompleteNtf(beacon, ERR_CONN_AUTH_FAIL);
                } else if (nReason == KBConnectionEvent.ConnTimeout) {
                    handleConnCompleteNtf(beacon, ERR_CONN_TIMEOUT);
                } else {
                    handleConnCompleteNtf(beacon, ERR_CONN_EXCEPTION_DISCONNECTED);
                }
            }
        }
    };

    private void handleConnCompleteNtf(KBeacon  beacon, int nErrorCode) {
        final String strDevMacAddr = beacon.getMac();
        if (nErrorCode == ERR_CONN_FAIL) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            new AlertDialog.Builder(DeviceScanActivity.this, R.style.AlertDialogStyle)
                    .setTitle(R.string.conn_error_title)
                    .setMessage(R.string.connect_error_timeout)
                    .setPositiveButton(R.string.Dialog_OK, null)
                    .show();
            mCurrentConnectingDevMac = null;
        } else if (nErrorCode == ERR_CONN_EXCEPTION_DISCONNECTED) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            mCurrentConnectingDevMac = null;
        } else if (nErrorCode == ERR_CONN_SUCCESS) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }

            startBeaconDFU(beacon);

            mCurrentConnectingDevMac = null;
            return;
        } else if (nErrorCode == ERR_CONN_AUTH_FAIL) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            mCurrentConnectingDevMac = null;

            final EditText inputServer = new EditText(DeviceScanActivity.this);
            AlertDialog.Builder builder = new AlertDialog.Builder(DeviceScanActivity.this, R.style.AlertDialogStyle);
            builder.setTitle(getString(R.string.auth_error_title));
            builder.setView(inputServer);
            builder.setNegativeButton(R.string.Dialog_Cancel, null);
            builder.setPositiveButton(R.string.Dialog_OK, null);
            final AlertDialog alertDialog = builder.create();
            alertDialog.show();

            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String strNewPassword = inputServer.getText().toString().trim();
                if (strNewPassword.length() < 8 || strNewPassword.length() > 16) {
                    Toast.makeText(DeviceScanActivity.this,
                            R.string.connect_error_auth_format,
                            Toast.LENGTH_SHORT).show();
                } else {
                    alertDialog.dismiss();

                    KBeacon blePerp = mBeaconMgr.getBeacon(strDevMacAddr);
                    startConnectDevice(blePerp);
                }
            });

            return;
        }
        else
        {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            mCurrentConnectingDevMac = null;
        }

        clearAllData();
        mDevListAdapter.notifyDataSetChanged();

        handleStartScan();
        invalidateOptionsMenu();
    }

    public void startBeaconDFU(final KBeacon beacon)
    {
        Intent intent = new Intent(this, CfgBeaconDFUActivity.class);
        intent.putExtra(CfgBeaconDFUActivity.DEVICE_MAC_ADDRESS, beacon.getMac());
        mProvisionLauncher.launch(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (mBeaconMgr.isScanning()) {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_dfu).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);

        } else {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_dfu).setVisible(true);
            menu.findItem(R.id.menu_refresh).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.menu_scan){
            handleStartScan();
            invalidateOptionsMenu();
        }
        else if(id == R.id.menu_stop){
            mManualStartScanning = false;
            mBeaconMgr.stopScanning();
            invalidateOptionsMenu();
        }
        else if(id == R.id.menu_dfu){
            mBeaconMgr.stopScanning();
            invalidateOptionsMenu();

            Intent intent = new Intent(DeviceScanActivity.this, CfgBeaconDFUHistory.class);
            startActivity(intent);
        }
        else if(id == R.id.menu_about){
            final Intent intent = new Intent(DeviceScanActivity.this, CfgAboutUs.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private void handleStartScan(){
        if (!checkBluetoothPermitAllowed())
        {
            toastShow("BLE scanning need location permission");
            mPendingScanning = true;
            return;
        }

        mLastScanTick = System.currentTimeMillis();
        if (mBeaconMgr.getScanMinRssiFilter() < -80) {
            mBeaconMgr.setScanMinRssiFilter(-80);
        }
        int nStartScan = mBeaconMgr.startScanning();
        mManualStartScanning = true;
        if (nStartScan == KBeaconsMgr.SCAN_ERROR_BLE_NOT_ENABLE) {
            toastShow("BLE function is not enable");
        }
        else if (nStartScan == KBeaconsMgr.SCAN_ERROR_NO_PERMISSION) {
            toastShow("BLE scanning has no location permission");
        }
        else
        {
            Log.v(TAG, "start scan success");
            mHandler.sendEmptyMessageDelayed(MSG_PERIOD_CHECK_CONNECT, 3000);
        }
    }

    public KBeacon getBeaconDevice(int nIndex) {
        return mBeaconsArray.get(nIndex);
    }

    public int getCount() {
        return mBeaconsArray.size();
    }

    public void onBeaconDiscovered(KBeacon[] beacons) {
        boolean bNeedRedrawn = false;
        ArrayList<KBeacon> modifyBeacons = new ArrayList<>(10);
        for (KBeacon beacon : beacons)
        {
            mDfuCfg.setLastUpdateTick(beacon.getMac(), System.currentTimeMillis());

            CfgDFURecord dfuStatus = mDfuCfg.getDfuRecord(beacon.getMac());
            if (dfuStatus != null) {
                if (dfuStatus.result == CfgDFURecord.DFU_SUCCESS
                        || dfuStatus.result == CfgDFURecord.DFU_NOT_NEED) {
                    continue;
                }
            }

            if (mBeaconsDictory.get(beacon.getMac()) == null) {
                bNeedRedrawn = true;
            }

            mBeaconsDictory.put(beacon.getMac(), beacon);
            modifyBeacons.add(beacon);
        }

        if (bNeedRedrawn) {
            if (mBeaconsDictory.size() > 0) {
                mBeaconsArray.clear();
                mBeaconsArray.addAll(mBeaconsDictory.values());

                mDevListAdapter.notifyDataSetChanged();
            }
        }else{
            for (KBeacon modifyBeacon : modifyBeacons)
            {
                int position = mBeaconsArray.indexOf(modifyBeacon);
                if (position != -1)
                {
                    int index = position - mListView.getFirstVisiblePosition();
                    if (index >= 0 && index < mListView.getChildCount()) {
                        View itemView = mListView.getChildAt(index);
                        if (itemView != null) {
                            mDevListAdapter.getView(position, itemView, mListView);
                        }
                    }
                }
            }
        }
    }

    public void clearAllData() {
        mBeaconsDictory.clear();
        mBeaconsArray.clear();
        mBeaconMgr.clearBeacons();
    }

    public void onCentralBleStateChang(int nNewState) {
        Log.e(TAG, "centralBleStateChang：" + nNewState);
    }

    public void onScanFailed(int errorCode) {
        Log.e(TAG, "Start N scan failed：" + errorCode);
        if (mScanFailedContinueNum >= MAX_ERROR_SCAN_NUMBER) {
            toastShow("scan encount error, error time:" + mScanFailedContinueNum);
        }
        mScanFailedContinueNum++;
    }




    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnRemoveAllFilter: {
                mBtnFilterTotal.setText("");
                mSeekBarRssi.setProgress(0);
                mEditFltDevName.setText("");
                mBtnRmvAllFilter.setVisibility(View.GONE);
                enableFilterSetting();
                break;
            }

            case R.id.imageButtonArrow:
            case R.id.btnFilterInfo: {
                checkDetailFilterDlg();
                break;
            }

            case R.id.listview:
            {
                mBtnRmvAllFilter.setVisibility(View.GONE);
            }

            case R.id.btmRemoveFilterName: {
                mEditFltDevName.setText("");
                break;
            }
        }
    }

    private void enableFilterSetting()
    {
        //filter
        String strFilterName = mEditFltDevName.getText().toString();
        boolean bChangeFilter = false;
        if (!strFilterName.equals(mBeaconMgr.getScanNameFilter()))
        {
            mBeaconMgr.setScanNameFilter(strFilterName, false);
            bChangeFilter = true;
        }

        if (mRssiFilterValue != mBeaconMgr.getScanMinRssiFilter())
        {
            mBeaconMgr.setScanMinRssiFilter(mRssiFilterValue);
            bChangeFilter = true;
        }

        if (bChangeFilter){
            clearAllData();
            mDevListAdapter.notifyDataSetChanged();
        }

        //update information
        String strTotalFilter = "";
        if (strFilterName.length() > 0) {
            strTotalFilter = strFilterName + ";";
        }
        if (mRssiFilterValue != -100) {
            strTotalFilter = strTotalFilter + String.valueOf(mRssiFilterValue) + getString(R.string.BEACON_RSSI_UINT);
        }
        mBtnFilterTotal.setText(strTotalFilter);


        //show remove button
        if (strTotalFilter.length() > 0) {
            mBtnRmvAllFilter.setVisibility(View.VISIBLE);
        } else {
            mBtnRmvAllFilter.setVisibility(View.GONE);
        }
    }

    void checkDetailFilterDlg()
    {
        if (mLayoutFilterRssi.getVisibility() == View.GONE) {
            mLayoutFilterRssi.setVisibility(View.VISIBLE);
            mLayoutFilterName.setVisibility(View.VISIBLE);
            mBtnFilterArrow.setBackground(getResources().getDrawable(R.drawable.uparrow));
            mBtnFilterArrow.setTag(1);
        } else {
            mLayoutFilterRssi.setVisibility(View.GONE);
            mLayoutFilterName.setVisibility(View.GONE);
            mBtnFilterArrow.setBackground(getResources().getDrawable(R.drawable.downarrow));
            mBtnFilterArrow.setTag(0);

            enableFilterSetting();
        }
    }

    void periodCheckConnection()
    {
        KBDfuPreference mDfuPref = KBDfuPreference.shareInstance(this);

        if (mBeaconMgr.isScanning()) {
            long currTick = System.currentTimeMillis();
            long oldestConnectTick = -1;
            KBeacon oldestBeacon = null;
            for (KBeacon beacon : mBeaconsArray) {
                CfgDFURecord result = mDfuPref.getDfuRecord(beacon.getMac());
                if (result != null)
                {
                    if (result.result == CfgDFURecord.DFU_NOT_NEED
                            || result.result == CfgDFURecord.DFU_SUCCESS) {
                        continue;
                    }
                }

                if (currTick - mDfuPref.getLastUpdateTick(beacon.getMac()) < 10 * 1000
                        && currTick - mDfuPref.getConnectingTime(beacon.getMac()) > 60 * 1000
                        && currTick - mDfuPref.getDisconnectingTime(beacon.getMac()) > 120 * 1000)
                {
                    if (oldestConnectTick < mDfuPref.getConnectingTime(beacon.getMac())) {
                        oldestBeacon = beacon;
                        oldestConnectTick = mDfuPref.getConnectingTime(beacon.getMac());
                    }
                }
            }

            if (oldestBeacon != null) {
                mBeaconMgr.stopScanning();
                invalidateOptionsMenu();

                Log.v(LOG_TAG, "start connect to device:" + oldestBeacon.getMac());
                startConnectDevice(oldestBeacon);
            }
        }

        mHandler.sendEmptyMessageDelayed(MSG_PERIOD_CHECK_CONNECT, 3000);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final KBeacon blePerp = mBeaconsArray.get(position);

        mBeaconMgr.stopScanning();
        invalidateOptionsMenu();

        startConnectDevice(blePerp);

        Log.e(TAG, "click id:" + id );
    }

    public void  startConnectDevice(final KBeacon blePerp)
    {
        if (blePerp == null){
            return;
        }
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            return;
        }

        mCurrentConnectingDevMac = blePerp.getMac();
        KBConnPara connPara = new KBConnPara();
        connPara.syncUtcTime = true;
        connPara.readCommPara = true;
        connPara.readSensorPara = false;
        connPara.readSlotPara = false;
        connPara.readTriggerPara = false;

        if (blePerp.connectEnhanced(mDfuCfg.getBeaconPassword(mCurrentConnectingDevMac),
                15*1000, connPara, mBeaconConnEvtCallback))
        {
            mDfuCfg.setConnectingTime(mCurrentConnectingDevMac, System.currentTimeMillis());

            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            String strMac = mCurrentConnectingDevMac.substring(mCurrentConnectingDevMac.length() - 8);
            mProgressDialog.setTitle(getString(R.string.ble_device_connecting) + " " + strMac);
            mProgressDialog.setIndeterminate(false);//设置进度条是否为不明确
            mProgressDialog.setCancelable(false);//设置进度条是否可以按退回键取消
            mProgressDialog.show();
        }
        else
        {
            toastShow(getString(R.string.device_busy));
        }
    }
    public class MsgHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REFERASH_VIEW: {
                    mHandler.removeMessages(MSG_REFERASH_VIEW);
                    mDevListAdapter.notifyDataSetChanged();
                    break;
                }


                case MSG_REFERASH_ONE_ITEM_VIEW:
                {
                    int position = msg.arg1;

                    if (mListView != null) {
                        View view = mListView.getChildAt(position - mListView.getFirstVisiblePosition());
                        if (view != null) {
                            mDevListAdapter.getView(position, view, mListView);
                        }
                    }
                    break;
                }

                case MSG_CHK_TIMER_MY:{
                    handlePeriodChk();
                    mHandler.sendEmptyMessageDelayed(MSG_CHK_TIMER_MY, CHK_TIMER_PERIOD);
                    break;
                }

                case MSG_PERIOD_CHECK_CONNECT:{
                    periodCheckConnection();
                    break;
                }

                default:
                    break;
            }
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        mDevListAdapter.notifyDataSetChanged();

        if (mManualStartScanning){

            handleStartScan();
            invalidateOptionsMenu();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();

        clearAllData();
        mBeaconMgr.stopScanning();
        invalidateOptionsMenu();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mBeaconMgr.stopScanning();
        clearAllData();
        invalidateOptionsMenu();
    }

    private void handlePeriodChk(){
        long currTick = System.currentTimeMillis();
    }


    private boolean checkBluetoothPermitAllowed(){
        if (!Utils.isLocationBluePermission(this)){

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT},
                        23);
            }
            else
            {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                        23);
            }

            return false;
        }
        else{
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 23){
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED){
                toastShow(getString(R.string.location_permit_for_ble_failed));
            }else{
                if (mPendingScanning) {
                    mPendingScanning = false;
                    handleStartScan();
                    invalidateOptionsMenu();
                }
            }
        }
    }


    public void toastShow(String strMsg)
    {
        Toast toast=Toast.makeText(this, strMsg, Toast.LENGTH_SHORT);

        toast.setGravity(Gravity.CENTER, 0, 0);

        toast.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            exitBy2Click(); //调用双击退出函数
        }
        return false;
    }
    /**
     * 双击退出函数
     */
    private static Boolean isExit = false;

    private void exitBy2Click() {
        Timer tExit = null;
        if (isExit == false) {
            isExit = true; // 准备退出
            Toast.makeText(this, R.string.double_click_quit, Toast.LENGTH_SHORT).show();
            tExit = new Timer();
            tExit.schedule(new TimerTask() {
                @Override
                public void run() {
                    isExit = false; // 取消退出
                }
            }, 2000); // 如果2秒钟内没有按下返回键，则启动定时器取消掉刚才执行的任务

        } else {
            finish();
            System.exit(0);
        }
    }

    private class EditChangedListener implements TextWatcher {
        private CharSequence temp;//监听前的文本
        private int editStart;//光标开始位置
        private int editEnd;//光标结束位置
        private final int charMaxNum = 10;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            temp = s;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.length() > 0)
            {
                mBtnRmvNameFilter.setVisibility(View.VISIBLE);
            }else{
                mBtnRmvNameFilter.setVisibility(View.GONE);
            }
            mDfuCfg.setFilterName(s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };
}