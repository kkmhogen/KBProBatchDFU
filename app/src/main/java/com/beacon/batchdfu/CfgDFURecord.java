package com.beacon.batchdfu;

import org.json.JSONException;
import org.json.JSONObject;

public class CfgDFURecord {

    public static int DFU_SUCCESS = 1;
    public static int DFU_FAIL = 2;
    public static int DFU_NOT_NEED = 3;

    String macAddress;
    String model;
    String ver;
    String dfuVer;
    Integer result;

    public String toString()
    {
        JSONObject object = new JSONObject();
        try {
            object.put("mac", macAddress);
            object.put("model", model);
            object.put("ver", ver);
            object.put("dfuVer", dfuVer);
            object.put("status", result);

        }catch (JSONException except)
        {
            except.printStackTrace();
        }

        return object.toString();
    }

    public static CfgDFURecord fromString(String strJson)
    {

        try {
            JSONObject object = new JSONObject(strJson);
            CfgDFURecord dfuRecord = new CfgDFURecord();

            dfuRecord.macAddress = object.getString("mac");
            dfuRecord.model = object.getString("model");
            dfuRecord.ver = object.getString("ver");
            dfuRecord.dfuVer = object.getString("dfuVer");
            dfuRecord.result = object.getInt("status");

            return dfuRecord;

        }catch (JSONException except)
        {
            except.printStackTrace();
        }

        return null;
    }
}
