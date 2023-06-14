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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;


/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class Utils {
    public static final int EDDYSTONE_MAX_URL_ENCODE_LEN = 18;

    public static final String INTENT_PARA_MAC_ADDRESS = "INTENT_PARA_MAC_ADDRESS";

    
    private static String getHashMapValue(HashMap<Integer, String> hashMap,int number){
    	String result =hashMap.get(number);
    	if(TextUtils.isEmpty(result)){
    		List<Integer> numbers = getElement(number);
    		result="";
    		for(int i=0;i<numbers.size();i++){
    			result+=hashMap.get(numbers.get(i))+"|";
    		}
    	}
    	return result;
    }

    /**
     * 位运算结果的反推函数10 -> 2 | 8;
     */
    static private List<Integer> getElement(int number){
    	List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < 32; i++){
            int b = 1 << i;
            if ((number & b) > 0) 
            	result.add(b);
        }
        
        return result;
    }

    private final int Tab_CRCCalTable[]= {(int)0x0000,(int)0xcc01, (int)0xd801,(int)0x1400,(int)0xf001,
            (int)0x3c00,(int)0x2800,(int)0xe401, (int)0xa001, (int)0x6c00, (int)0x7800,
            (int)0xb401,(int)0x5000,(int)0x9c01,(int)0x8801,(int)0x4400};

    private int V_CRCDataValue = 0;
    private void F_CalcCRCDataValue(byte input_crc)
    {
        int temp0;
        byte i;

        temp0 = (V_CRCDataValue >> 4);
        i = (byte)((V_CRCDataValue^input_crc) & 0x0f);
        temp0 = (temp0 ^ Tab_CRCCalTable[i]);

        V_CRCDataValue = (temp0>>4);
        i = (byte)((temp0^(input_crc>>4)) & 0x0f);
        V_CRCDataValue = (V_CRCDataValue ^ Tab_CRCCalTable[i]);
    }

    public int getCRC16(byte a1, byte a2, byte a3, byte a4, byte a5)
    {
        V_CRCDataValue = 0x0000;
        F_CalcCRCDataValue(a1);
        F_CalcCRCDataValue(a2);
        F_CalcCRCDataValue(a3);
        F_CalcCRCDataValue(a4);
        F_CalcCRCDataValue(a5);

        return V_CRCDataValue;
    }

    public int getCRC16Ex(byte a1,byte a2, byte a3, byte a4, byte a5, byte a6)
    {
        V_CRCDataValue = 0x0000;

        F_CalcCRCDataValue(a1);
        F_CalcCRCDataValue(a2);
        F_CalcCRCDataValue(a3);
        F_CalcCRCDataValue(a4);
        F_CalcCRCDataValue(a5);
        F_CalcCRCDataValue(a6);

        return V_CRCDataValue;
    }

    public static boolean isByteValueEqual(byte[] byToBeCompare, byte[] byCompared){
        if (byToBeCompare.length != byCompared.length){
            return false;
        }

        for (int i = 0; i < byToBeCompare.length; i++){
            if (byToBeCompare[i] != byCompared[i]){
                return false;
            }
        }

        return true;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static boolean isLocationBluePermission(final Context context) {
        if (!Utils.isMPhone()) {
            return true;
        } else {
            boolean result = true;
            if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                result = false;
            }
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                result = false;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    result = false;
                }

                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    result = false;
                }
            }

            return result;
        }
    }

    public static boolean isMPhone() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static byte[] hexStringToBytes(String hexString){
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        char []hexCharacter = hexString.toCharArray();
        for (int i = 0; i < hexCharacter.length; i++){
            if (-1 == charToByte(hexCharacter[i])){
                return null;
            }
        }

        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));

        }
        return d;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }
    
    public static String bytesToHexString(byte[] src){  
        StringBuilder stringBuilder = new StringBuilder("");  
        if (src == null || src.length <= 0) {  
            return null;  
        }  
        for (int i = 0; i < src.length; i++) {  
            int v = src[i] & 0xFF;  
            String hv = Integer.toHexString(v);  
            if (hv.length() < 2) {  
                stringBuilder.append(0);  
            }  
            stringBuilder.append(hv);  
        }  
        return stringBuilder.toString();  
    }

    public static String macAddressAddOne(String adres) {
        String address = adres;
        String[] addressArr = address.split(":");
        String strHex = addressArr[5];
        int l = strHex.length() / 2;
        byte[] ret = new byte[l];
        for (int i = 0; i < l; i++) {
            ret[i] = (byte) Integer
                    .valueOf(strHex.substring(i * 2, i * 2 + 2), 16).byteValue();
        }
        if (ret[0] == -1) {
            ret[0] = 0;
        } else {
            ret[0] += 1;
        }
        String last = bytesToHexString(ret);
        String newAddress = addressArr[0] + ":" + addressArr[1] + ":" + addressArr[2] + ":" + addressArr[3] + ":" + addressArr[4] + ":" + last;
        return newAddress;
    }

    public  final static int EDDYSTONE_URL_ENCODING_MAX = 14;

    // # of URL Scheme Prefix types
    public  static final int EDDYSTONE_URL_PREFIX_MAX = 4;

    // Array of URL Scheme Prefices
    public static String []eddystoneURLPrefix = {"http://www.",  "https://www.",
        "http://",  "https://"};

    // Array of URLs to be encoded
    public static String []eddystoneURLEncoding = {
                ".com/",
                ".org/",
                ".edu/",
                ".net/",
                ".info/",
                ".biz/",
                ".gov/",
                ".com/",
                ".org/",
                ".edu/",
                ".net/",
                ".info/",
                ".biz/",
                ".gov/"
    };


    public static int EddystoneBeacon_encodeURL(char[] urlOrg, char[] urlEnc)
    {
        int i, j, k;
        int encIndex = 0;

        // search for a matching prefix
        String strSrcOrgData = new String(urlOrg);
        int nWebHeadIndex = 0;
        for (i = 0; i < EDDYSTONE_URL_PREFIX_MAX; i++)
        {
            nWebHeadIndex = strSrcOrgData.indexOf(eddystoneURLPrefix[i]);
            if (nWebHeadIndex != -1)
            {
                break;
            }
        }
        if (nWebHeadIndex == -1)
        {
            return 0;       // wrong prefix
        }

        // use the matching prefix number
        urlEnc[encIndex] = (char)i;
        encIndex++;

        //find the tail prefex
        int  nWebTailIndex = 0;
        for (j = 0; j < EDDYSTONE_URL_ENCODING_MAX; j++) {
            nWebTailIndex = strSrcOrgData.indexOf(eddystoneURLEncoding[j]);
            if (nWebTailIndex != -1)
            {
                break;
            }
        }
        if (nWebTailIndex == -1){
            nWebTailIndex = strSrcOrgData.length();
        }

        //add middle url info
        for (k = eddystoneURLPrefix[i].length(); k < nWebTailIndex; k++) {
            if (encIndex >= EDDYSTONE_MAX_URL_ENCODE_LEN)
            {
                return -1;
            }
            urlEnc[encIndex++] = urlOrg[k];
        }

        //add url tail
        if (j != EDDYSTONE_URL_ENCODING_MAX) {
            if (encIndex >= EDDYSTONE_MAX_URL_ENCODE_LEN)
            {
                return -1;
            }

            urlEnc[encIndex++] = (char)j;

            //remain
            for (k = nWebTailIndex + eddystoneURLEncoding[j].length(); k < urlOrg.length; k++){
                if (encIndex >= EDDYSTONE_MAX_URL_ENCODE_LEN)
                {
                    return -1;
                }

                urlEnc[encIndex++] = urlOrg[k];
            }
        }

        return encIndex;
    }
    public static int EddystoneBeacon_decodeURL(char[] urlOrg, int nSrcLength, char[] urlDec)
    {
        int i, j, k;
        int decIndex = 0;

        //first
        if (urlOrg[0] > EDDYSTONE_URL_PREFIX_MAX){
            return 0;
        }

        //add url head
        char[] urlPrefex = eddystoneURLPrefix[urlOrg[0]].toCharArray();
        for (i = 0; i < urlPrefex.length; i++){
            urlDec[decIndex++] = urlPrefex[i];
        }

        //add middle web
        for (j = 1; j < nSrcLength; j++){
            if (urlOrg[j] <= EDDYSTONE_URL_ENCODING_MAX) {
                char[] urlSuffix = eddystoneURLEncoding[urlOrg[j]].toCharArray();
                for (k = 0; k < urlSuffix.length; k++) {
                    urlDec[decIndex++] = urlSuffix[k];
                }
            }
            else
            {
                urlDec[decIndex++] = urlOrg[j];
            }
        }

        return decIndex;
    }

    public static String EddystoneBeacon_DecodeURL(byte[] urlOrg, int nSrcLength)
    {
        char []urlCharSrc = new char[18];
        char []urlCharDec = new char[40];
        String strDecode = null;

        if (urlOrg == null){
            return null;
        }

        for (int i = 0; i < nSrcLength; i++) {
            urlCharSrc[i] += (char) urlOrg[i];
        }

        int nDecLen = EddystoneBeacon_decodeURL(urlCharSrc, nSrcLength, urlCharDec);
        if (nDecLen != 0)
        {
            strDecode = "";
            for (int i = 0; i < nDecLen; i++){
                strDecode += urlCharDec[i];
            }
        }

        return strDecode;
    }

    public static String FormatHexUUID2User(String strUUID)
    {
        strUUID = strUUID.toUpperCase().replace("0X", "");
        if (strUUID.length() != 32)
        {
            return "";
        }

        String strUserUUID;
        strUserUUID = strUUID.substring(0, 8);
        strUserUUID += "-";

        strUserUUID += strUUID.substring(8, 12);
        strUserUUID += "-";

        strUserUUID += strUUID.substring(12, 16);
        strUserUUID += "-";

        strUserUUID += strUUID.substring(16, 20);
        strUserUUID += "-";

        strUserUUID += strUUID.substring(20);

        return strUserUUID;
    }

    public static String ReadTxtFile(File file)
    {
       StringBuilder content = new StringBuilder();

        try {
            InputStream instream = new FileInputStream(file);
            InputStreamReader inputreader = new InputStreamReader(instream);
            BufferedReader buffreader = new BufferedReader(inputreader);
            String line;
            //分行读取
            while (( line = buffreader.readLine()) != null) {
                content.append(line);
            }
            instream.close();
        }
        catch (java.io.FileNotFoundException e)
        {
            return null;
        }
        catch (IOException e)
        {
            return null;
        }

        return content.toString();
    }
}
