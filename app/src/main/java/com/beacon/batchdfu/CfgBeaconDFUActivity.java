package com.beacon.batchdfu;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.ProgressBar;
import android.widget.TextView;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import androidx.annotation.NonNull;

import com.kkmcn.kbeaconlib2.KBCfgPackage.KBCfgCommon;
import com.kkmcn.kbeaconlib2.KBConnState;
import com.kkmcn.kbeaconlib2.KBeacon;
import com.kkmcn.kbeaconlib2.KBeaconsMgr;

import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceController;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

public class CfgBeaconDFUActivity extends AppBaseActivity implements KBeacon.ConnStateDelegate {
    private static String LOG_TAG = "CfgNBDFU";
    public static String DEVICE_MAC_ADDRESS = "DEVICE_MAC_ADDRESS";
    public static String DEVICE_MODEL = "DEVICE_MODEL";
    public static String CURR_VERSION = "CURR_VERSION";
    public static String DFU_VERSION = "DFU_VERSION";

    private boolean mInDfuState = false;
    private KBFirmwareDownload firmwareDownload;
    private DfuServiceController controller;
    private TextView mUpdateStatusLabel, mUpdateNotesLabel, mNewVersionLabel;
    private ProgressBar mProgressBar;
    private KBeacon.ConnStateDelegate mPrivousDelegation;
    private DfuServiceInitiator starter;
    private KBeacon mBeacon;
    private KBCfgCommon mCfgCommon;

    private String mDestFirmwareFileName;
    private String mDestVersion = "";

    KBDfuPreference mDfuPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cfg_beacon_dfu);

        final Intent intent = getIntent();
        String mMacAddress = intent.getStringExtra(DEVICE_MAC_ADDRESS);
        if (mMacAddress == null) {
            finish();
            return;
        }
        KBeaconsMgr mBluetoothMgr = KBeaconsMgr.sharedBeaconManager(this);
        mBeacon = mBluetoothMgr.getBeacon(mMacAddress);
        mCfgCommon = mBeacon.getCommonCfg();
        mBeacon.setConnStateDelegate(mBeaconConnEvtCallback);

        mUpdateStatusLabel = (TextView) findViewById(R.id.textStatusDescription);
        mProgressBar = (ProgressBar)findViewById(R.id.progressBar);

        mNewVersionLabel = (TextView) findViewById(R.id.releaseNotesTitle);
        mUpdateNotesLabel = (TextView) findViewById(R.id.releaseNotes);

        mInDfuState = false;
        firmwareDownload = new KBFirmwareDownload(this);

        mDfuPref = KBDfuPreference.shareInstance(this);
        this.downloadFirmwareInfo();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DfuServiceInitiator.createDfuNotificationChannel(CfgBeaconDFUActivity.this);
        }

    }

    private final KBeacon.ConnStateDelegate mBeaconConnEvtCallback = new KBeacon.ConnStateDelegate()
    {
        @Override
        public void onConnStateChange(KBeacon beacon, KBConnState state, int nReason) {
            Log.e(LOG_TAG, "Device disconnected:" + state);
            if (state == KBConnState.Disconnected){
                mDfuPref.setDisconnectingTime(mBeacon.getMac(), System.currentTimeMillis());
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBeacon.disconnect();
        mBeacon.setConnStateDelegate(null);
    }

    private final DfuProgressListener dfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(final String deviceAddress) {
            mUpdateStatusLabel.setText(R.string.dfu_status_connecting);
        }

        @Override
        public void onDfuProcessStarting(final String deviceAddress) {
            mUpdateStatusLabel.setText(R.string.dfu_status_starting);
        }

        public void onDeviceConnected(@NonNull final String deviceAddress) {
            // empty default implementation
            mUpdateStatusLabel.setText(R.string.UPDATE_CONNECTED);
        }

        public void onDeviceDisconnecting(@NonNull final String deviceAddress) {
            // empty default implementation
            mUpdateStatusLabel.setText(R.string.dfu_status_disconnecting);
        }

        public void onDeviceDisconned(@NonNull final String deviceAddress) {
            // empty default implementation
            mUpdateStatusLabel.setText(R.string.UPDATE_DISCONNECTED);
        }

        @Override
        public void onProgressChanged(@NonNull final String deviceAddress, final int percent,
                                      final float speed, final float avgSpeed,
                                      final int currentPart, final int partsTotal) {
            mProgressBar.setProgress(percent);
            mUpdateStatusLabel.setText(R.string.UPDATE_UPLOADING);
        }

        @Override
        public void onDfuCompleted(@NonNull final String deviceAddress) {
            // empty default implementation
            mUpdateStatusLabel.setText(R.string.UPDATE_COMPLETE);
            mInDfuState = false;
            dfuComplete(getString(R.string.UPDATE_COMPLETE), CfgDFURecord.DFU_SUCCESS);
        }

        @Override
        public void onDfuAborted(@NonNull final String deviceAddress) {
            mUpdateStatusLabel.setText(R.string.UPDATE_ABORTED);
            mInDfuState = false;
            dfuComplete(getString(R.string.UPDATE_COMPLETE), CfgDFURecord.DFU_FAIL);
        }

        @Override
        public void onError(@NonNull final String deviceAddress,
                            final int error, final int errorType, final String message) {
            // empty default implementation
            mUpdateStatusLabel.setText(R.string.UPDATE_ABORTED);
            mInDfuState = false;
            dfuComplete(message, CfgDFURecord.DFU_FAIL);
        }
    };

    private void dfuComplete(String strDesc, int result)
    {
        Log.e(LOG_TAG, strDesc);

        mBeacon.disconnect();
        Intent dfuData = new Intent();
        dfuData.putExtra(DEVICE_MAC_ADDRESS, mBeacon.getMac());
        int tailIndex = mCfgCommon.getModel().indexOf("_");
        String model = "";
        if (tailIndex != -1){
            model = mCfgCommon.getModel().substring(0, tailIndex);
        }
        dfuData.putExtra(DEVICE_MODEL, model);
        dfuData.putExtra(DFU_VERSION, mDestVersion);
        dfuData.putExtra(CURR_VERSION, mCfgCommon.getVersion());

        setResult(result, dfuData);
        CfgBeaconDFUActivity.this.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();

        DfuServiceListenerHelper.registerProgressListener(this, dfuProgressListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        DfuServiceListenerHelper.unregisterProgressListener(this, dfuProgressListener);
    }

    @Override
    public void onConnStateChange(KBeacon beacon, KBConnState state, int nReason) {
        if (state == KBConnState.Connected)
        {
            if (mInDfuState)
            {
                Log.v(LOG_TAG, "Disconnection for DFU");
            }
        }
    }

    private void startBeaconDFU(File file)
    {
        mInDfuState = true;

        starter = new DfuServiceInitiator(CfgBeaconDFUActivity.this.mBeacon.getMac())
                .setDeviceName(CfgBeaconDFUActivity.this.mBeacon.getName())
                .setKeepBond(false);

        starter.setPrepareDataObjectDelay(300L);
        starter.setZip(null, file.getPath());

        controller = starter.start(CfgBeaconDFUActivity.this, DFUService.class);
    }

    private void updateFirmware() {
        File existFile = new File(mDestFirmwareFileName);
        if (existFile.exists())
        {
            startBeaconDFU(existFile);
        }
        else
        {
            firmwareDownload.downloadFirmwareData(mDestFirmwareFileName,
                    (bSuccess, file, error) -> {
                        if (bSuccess) {
                            startBeaconDFU(file);
                        } else {
                            mUpdateStatusLabel.setText(R.string.UPDATE_NETWORK_FAIL);
                            dfuComplete(getString(R.string.UPDATE_NETWORK_FAIL), CfgDFURecord.DFU_FAIL);
                        }
                    });
        }
    }

    private void makeSureUpdateSelection( ) {
        mProgressBar.setProgress(0);
        mInDfuState = true;

        //update
        mPrivousDelegation = mBeacon.getConnStateDelegate();
        mBeacon.setConnStateDelegate(CfgBeaconDFUActivity.this);
        updateFirmware();
    }


    private void downloadFirmwareInfo() {
        mUpdateNotesLabel.setText(R.string.DEVICE_CHECK_UPDATE);

        firmwareDownload.downloadFirmwareInfo(mCfgCommon.getModel(), 10* 1000,
                (bSuccess, firmwareInfo, error) -> {
                    if (bSuccess) {
                        if (firmwareInfo == null)
                        {
                            dfuComplete(getString(R.string.NB_network_cloud_server_error), CfgDFURecord.DFU_FAIL);
                            return;
                        }

                        if (!firmwareInfo.has(mBeacon.hardwareVersion()))
                        {
                            dfuComplete(getString(R.string.NB_network_file_not_exist), CfgDFURecord.DFU_FAIL);
                            return;
                        }

                        //check if json file valid
                        JSONArray firmwareVerList = null;
                        try {
                            firmwareVerList = firmwareInfo.getJSONArray(mBeacon.hardwareVersion());
                        }catch (JSONException except){
                            except.printStackTrace();
                        }
                        if (firmwareVerList == null){
                            dfuComplete(getString(R.string.NB_network_file_not_json), CfgDFURecord.DFU_FAIL);
                            return;
                        }

                        String currVerDigital = mCfgCommon.getVersion().substring(1);
                        StringBuilder versionNotes = new StringBuilder();
                        for (int i = 0; i < firmwareVerList.length(); i++) {
                            JSONObject object;
                            try
                            {
                                object = (JSONObject)firmwareVerList.get(i);
                                if (!object.has("appVersion"))
                                {
                                    dfuComplete(getString(R.string.NB_network_cloud_server_error), CfgDFURecord.DFU_FAIL);
                                    return;
                                }

                                String destVersion = (String) object.getString("appVersion");
                                String remoteVerDigital = destVersion.substring(1);
                                if (Float.parseFloat(currVerDigital) < Float.parseFloat(remoteVerDigital)) {
                                    if (!object.has("appFileName"))
                                    {
                                        dfuComplete(getString(R.string.NB_network_cloud_server_error), CfgDFURecord.DFU_FAIL);
                                        return;
                                    }
                                    String appFileName = (String) object.get("appFileName");

                                    //check notes
                                    if (object.has("note"))
                                    {
                                        versionNotes.append(object.getString("note"));
                                        versionNotes.append("\n");
                                    }

                                    if (i == firmwareVerList.length() - 1)
                                    {
                                        mDestFirmwareFileName = appFileName;
                                        mDestVersion = destVersion;

                                        //found new version
                                        mUpdateStatusLabel.setText(R.string.UPDATE_FOUND_NEW_VERSION);
                                        mNewVersionLabel.setText(destVersion);
                                        mUpdateNotesLabel.setText(versionNotes.toString());

                                        makeSureUpdateSelection();
                                        return;
                                    }
                                }
                            }
                            catch (JSONException excpt)
                            {
                                excpt.printStackTrace();
                                dfuComplete(getString(R.string.NB_network_cloud_server_error), CfgDFURecord.DFU_FAIL);
                                return;
                            }
                        }

                        dfuComplete(getString(R.string.DEVICE_LATEST_VERSION), CfgDFURecord.DFU_NOT_NEED);
                    }
                    else{
                        dfuComplete(getString(R.string.UPDATE_NETWORK_FAIL), CfgDFURecord.DFU_FAIL);
                    }
                });
    }


}
