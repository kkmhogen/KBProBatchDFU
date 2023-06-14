package com.beacon.batchdfu;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class KBDfuPreference {
    private Context mContext;

    //dfu result list
    private final static String DFU_SETTING_INFO = "DFU_RECORD_INFO";
    private final static String DFU_STATUS = "DFU_RECORD";

    //control info
    private final static String FILTER_SETTING_INFO = "FILTER_SETTING_INFO";
    private final static String FILTER_MIN_RSSI = "FILTER_MIN_RSSI";
    private final static String FILTER_NAME = "FILTER_NAME";
    private final static String FILTER_DEV_PASSWORD = "FILTER_DEV_PASSWORD";

    //beacon connect
    private final static String CONN_SETTING_INFO = "CONN_SETTING_INFO";
    private final static String CONN_PREFEX = "CONN_PREFEX";

    private final static String DISCONN_SETTING_INFO = "DISCONN_SETTING_INFO";
    private final static String DISCONN_PREFEX = "DISCONN_PREFEX";
    private final static String SCAN_TIME = "SCAN_TIME";

    static private KBDfuPreference sPrefMgr = null;
    HashMap<String, CfgDFURecord> mDfuRecordMap = new HashMap<>(100);

    HashMap<String, Long> mBeaconUpdateMap = new HashMap<>(100);

    HashMap<String, Long> mBeaconDisconnectedMap = new HashMap<>(100);

    HashMap<String, Long> mBeaconConnectedMap = new HashMap<>(100);

    static public synchronized KBDfuPreference shareInstance(Context ctx){
        if (sPrefMgr == null){
            sPrefMgr = new KBDfuPreference();
            sPrefMgr.initSetting(ctx);
        }

        return sPrefMgr;
    }

    private KBDfuPreference(){
    }

    //初始化配置
    public void initSetting(Context ctx){
        mContext = ctx;
        loadDfuHistory();
    }

    public CfgDFURecord getDfuRecord(String strMac){
        return mDfuRecordMap.get(strMac);
    }


    public void setDfuResult(CfgDFURecord dfuRecord)
    {
        String key = DFU_STATUS + dfuRecord.macAddress;
        SharedPreferences shareReference = mContext.getSharedPreferences(DFU_SETTING_INFO, mContext.MODE_PRIVATE);
        SharedPreferences.Editor edit = shareReference.edit();
        edit.putString(key, dfuRecord.toString());
        edit.apply();

        mDfuRecordMap.put(dfuRecord.macAddress, dfuRecord);
    }

    private void loadDfuHistory()
    {
        SharedPreferences shareReference = mContext.getSharedPreferences(DFU_SETTING_INFO, mContext.MODE_PRIVATE);
        Map<String,?> allDfuValue = shareReference.getAll();

        for (Map.Entry<String, ?> entry : allDfuValue.entrySet()) {
            String strMacAddress = entry.getKey().substring(DFU_STATUS.length());
            String strObject = (String)entry.getValue();
            CfgDFURecord record = CfgDFURecord.fromString(strObject);
            if (record != null) {
                mDfuRecordMap.put(strMacAddress, record);
            }
        }

        //all conn time
        shareReference = mContext.getSharedPreferences(CONN_SETTING_INFO, mContext.MODE_PRIVATE);
        Map<String,?> allConnValue = shareReference.getAll();
        for (Map.Entry<String, ?> entry : allConnValue.entrySet()) {
            String strMacAddress = entry.getKey().substring(CONN_PREFEX.length());
            Long connTime = (Long)entry.getValue();
            mBeaconConnectedMap.put(strMacAddress, connTime);
        }

        //all dis connected time
        shareReference = mContext.getSharedPreferences(DISCONN_SETTING_INFO, mContext.MODE_PRIVATE);
        Map<String,?> allDisConnValue = shareReference.getAll();
        for (Map.Entry<String, ?> entry : allDisConnValue.entrySet()) {
            String strMacAddress = entry.getKey().substring(DISCONN_PREFEX.length());
            Long disConnTime = (Long)entry.getValue();
            mBeaconDisconnectedMap.put(strMacAddress, disConnTime);
        }
    }

    public ArrayList<CfgDFURecord> getDfuResultList()
    {
        ArrayList<CfgDFURecord> arrList = new ArrayList<>(10);
        for (Map.Entry<String, CfgDFURecord> entry : mDfuRecordMap.entrySet())
        {
            arrList.add(entry.getValue());
        }

        return arrList;
    }

    public void clearDfuList()
    {
        SharedPreferences shareReference = mContext.getSharedPreferences(DFU_SETTING_INFO, mContext.MODE_PRIVATE);
        SharedPreferences.Editor edit = shareReference.edit();
        for (Map.Entry<String, CfgDFURecord> entry : mDfuRecordMap.entrySet()) {
            String key = DFU_STATUS + entry.getKey();
            edit.remove(key);
        }
        edit.apply();

        mDfuRecordMap.clear();
    }

    public void setBeaconPassword(String strMac, String strPwd){
        SharedPreferences shareReference = mContext.getSharedPreferences(FILTER_SETTING_INFO, mContext.MODE_PRIVATE);
        SharedPreferences.Editor edit = shareReference.edit();
        edit.putString(FILTER_DEV_PASSWORD+strMac, strPwd);
        edit.apply();
    }

    public String getBeaconPassword(String strMac)
    {
        String key = FILTER_DEV_PASSWORD + strMac;
        SharedPreferences shareReference = mContext.getSharedPreferences(FILTER_SETTING_INFO, mContext.MODE_PRIVATE);
        return shareReference.getString(key, "0000000000000000");
    }


    public void setFilterRssi(int rssi){
        SharedPreferences shareReference = mContext.getSharedPreferences(FILTER_SETTING_INFO, mContext.MODE_PRIVATE);
        SharedPreferences.Editor edit = shareReference.edit();
        edit.putInt(FILTER_MIN_RSSI, rssi);
        edit.apply();
    }

    public int getFilterRssi(){
        SharedPreferences shareReference = mContext.getSharedPreferences(FILTER_SETTING_INFO, mContext.MODE_PRIVATE);
        return shareReference.getInt(FILTER_MIN_RSSI, -80);
    }

    public void setFilterName(String strName){
        SharedPreferences shareReference = mContext.getSharedPreferences(FILTER_SETTING_INFO, mContext.MODE_PRIVATE);
        SharedPreferences.Editor edit = shareReference.edit();
        edit.putString(FILTER_NAME, "KBPro");
        edit.apply();
    }

    public String getFilterName(){
        SharedPreferences shareReference = mContext.getSharedPreferences(FILTER_SETTING_INFO, mContext.MODE_PRIVATE);
        return shareReference.getString(FILTER_NAME, "");
    }


    public void setConnectingTime(String strMac, Long time){
        mBeaconConnectedMap.put(strMac, time);
        String key = CONN_PREFEX + strMac;
        SharedPreferences shareReference = mContext.getSharedPreferences(CONN_SETTING_INFO, mContext.MODE_PRIVATE);
        SharedPreferences.Editor edit = shareReference.edit();
        edit.putLong(key, time);
        edit.apply();
    }

    public long getConnectingTime(String strMac){
        Long connTime = mBeaconConnectedMap.get(strMac);
        if (connTime == null){
            return 0L;
        }
        return connTime;
    }

    public void setDisconnectingTime(String strMac, Long time){
        mBeaconDisconnectedMap.put(strMac, time);
        String key = DISCONN_PREFEX + strMac;
        SharedPreferences shareReference = mContext.getSharedPreferences(DISCONN_SETTING_INFO, mContext.MODE_PRIVATE);
        SharedPreferences.Editor edit = shareReference.edit();
        edit.putLong(key, time);
        edit.apply();
    }

    public Long getDisconnectingTime(String strMac){
        Long disConnTime = mBeaconDisconnectedMap.get(strMac);
        if (disConnTime == null){
            return 0L;
        }
        return disConnTime;
    }

    public void setLastUpdateTick(String strMac, Long updateTime)
    {
        mBeaconUpdateMap.put(strMac, updateTime);
    }

    public long getLastUpdateTick(String strMac){
        Long lastUpdate = mBeaconUpdateMap.get(strMac);
        if (lastUpdate == null){
            lastUpdate = 0L;
        }
        return lastUpdate;
    }
}
