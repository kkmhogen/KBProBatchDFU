package com.beacon.batchdfu;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvPacketIBeacon;
import com.kkmcn.kbeaconlib2.KBAdvPackage.KBAdvType;
import com.kkmcn.kbeaconlib2.KBeacon;

import static java.lang.StrictMath.abs;

public class LeDeviceListAdapter extends BaseAdapter {
	public interface ListDataSource {
		KBeacon getBeaconDevice(int nIndex);

		int getCount();
	}

	// Adapter for holding devices found through scanning.
	private Context mContext;
	private ListDataSource mDataSource;

	public LeDeviceListAdapter(Context c, ListDataSource dataSource) {
		super();
		mContext = c;
		mDataSource = dataSource;
	}

	@Override
	public int getCount() {
		return mDataSource.getCount();
	}

	@Override
	public Object getItem(int i) {
		return mDataSource.getBeaconDevice(i);
	}

	@Override
	public long getItemId(int i) {
		return i;
	}


	@Override
	public View getView(int i, View view, ViewGroup viewGroup) {
		ViewHolder viewHolder;
		// General ListView optimization code.
		if (view == null) {
			view = LayoutInflater.from(mContext).inflate(R.layout.listitem_device, null);
			viewHolder = new ViewHolder();
			viewHolder.deviceName = (TextView) view
					.findViewById(R.id.txt_beacon_adv_name);
			viewHolder.rssiState = (TextView) view
					.findViewById(R.id.txt_beacon_adv_rssi);
			viewHolder.deviceMacAddr = (TextView) view
					.findViewById(R.id.beacon_mac_address);
			viewHolder.deviceBattery = (TextView) view
					.findViewById(R.id.txt_beacon_adv_battery_percent);


			//iBeacon
			viewHolder.deviceIBeaconLay = (LinearLayout) view
					.findViewById(R.id.beacon_ibeacon_type);
			viewHolder.deviceIBeaconUUID = (TextView) view
					.findViewById(R.id.txt_beacon_adv_uuid);
			viewHolder.deviceIBeaconMajor = (TextView) view
					.findViewById(R.id.txt_beacon_adv_major);
			viewHolder.deviceIBeaconMinor = (TextView) view
					.findViewById(R.id.txt_beacon_adv_minor);
			viewHolder.deviceIBeaconRefPower = (TextView) view
					.findViewById(R.id.txt_beacon_ibeacon_ref_power);
			view.setTag(viewHolder);
		} else {
			viewHolder = (ViewHolder) view.getTag();
		}

		KBeacon device = mDataSource.getBeaconDevice(i);
		if (device == null){
			return null;
		}

		//device name
		if (device.getName() != null && device.getName().length() > 0)
		{
			viewHolder.deviceName.setText(device.getName());
		}else{
			viewHolder.deviceName.setText(R.string.BEACON_INVALID_VALUE);
		}

		//rssi
		if (device.getRssi() != null) {
			String strRssiValue = device.getRssi() + mContext.getString(R.string.BEACON_RSSI_UINT);
			viewHolder.rssiState.setText(strRssiValue);
		}

		//mac address
		String strMacAddress =  mContext.getString(R.string.beacon_mac) + device.getMac();
		viewHolder.deviceMacAddr.setText(strMacAddress);

		//battery
		String strBatteryValue = mContext.getString(R.string.battery) + mContext.getString(R.string.BEACON_INVALID_VALUE);
		if (device.getBatteryPercent() != null)
		{
			strBatteryValue = mContext.getString(R.string.battery) + device.getBatteryPercent()
					+ mContext.getString(R.string.battery_uint);
		}
		viewHolder.deviceBattery.setText(strBatteryValue);

		//ibeacon type
		KBAdvPacketIBeacon advIBeacon = (KBAdvPacketIBeacon)device.getAdvPacketByType(KBAdvType.IBeacon);
		if (advIBeacon != null) {
			viewHolder.deviceIBeaconLay.setVisibility(View.VISIBLE);

			//uuid
			String strUUID = advIBeacon.getUuid();
			viewHolder.deviceIBeaconUUID.setText(strUUID);

			//conn major
			viewHolder.deviceIBeaconMajor.setText(String.valueOf(advIBeacon.getMajorID()));

			//conn minor
			viewHolder.deviceIBeaconMinor.setText(String.valueOf(advIBeacon.getMinorID()));


			//ref power
			//String strRefPower =   advIBeacon.getRefTxPower() + mContext.getString(R.string.BEACON_RSSI_UINT);
            double fAbsFactory = abs(advIBeacon.getRssi()) - abs(advIBeacon.getRefTxPower());
            fAbsFactory = fAbsFactory / 20.0;
            double distance = Math.pow(10, fAbsFactory);
            String strDistance = String.format("%.1f", distance);

			viewHolder.deviceIBeaconRefPower.setText(strDistance);
		}else{
			viewHolder.deviceIBeaconLay.setVisibility(View.GONE);
		}

		return view;
	}

	class ViewHolder {
		TextView deviceName;      //名称
		TextView rssiState;     //状态
		TextView deviceBattery;
		TextView deviceMacAddr;

		LinearLayout deviceIBeaconLay;
		TextView deviceIBeaconMajor;
		TextView deviceIBeaconMinor;
		TextView deviceIBeaconUUID;
		TextView deviceIBeaconRefPower;
	}
}
