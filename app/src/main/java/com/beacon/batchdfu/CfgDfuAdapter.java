package com.beacon.batchdfu;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;



public class CfgDfuAdapter  extends BaseAdapter {
    public List<CfgDFURecord> list;
    public LayoutInflater inflater;
    private Context mCtx;

    public CfgDfuAdapter(Context context, List<CfgDFURecord> list) {
        this.list = list;
        mCtx = context;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    public void updateView(List<CfgDFURecord> nowList)
    {
        this.list = nowList;
        this.notifyDataSetChanged();//强制动态刷新数据进而调用getView方法
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
        ViewHolder holder = null;
        if(convertView == null)
        {
            view = inflater.inflate(R.layout.ele_dfu_record, null);
            holder = new ViewHolder();
            holder.txtViewMac =  view.findViewById(R.id.dfu_mac);
            holder.txtViewStatus = view.findViewById(R.id.dfu_result);
            holder.txtModel = view.findViewById(R.id.dfu_model);
            holder.txtVer = view.findViewById(R.id.dfu_ver);
            holder.txtDestVer = view.findViewById(R.id.dfu_dest_ver);
            holder.txtViewStatus = (TextView)view.findViewById(R.id.dfu_result);
            view.setTag(holder);//为了复用holder
        }else
        {
            view = convertView;
            holder = (ViewHolder) view.getTag();
        }
        CfgDFURecord record = list.get(position);
        holder.txtViewMac.setText(record.macAddress);
        holder.txtModel.setText(record.model);
        holder.txtVer.setText(record.ver);
        holder.txtDestVer.setText(record.dfuVer);
        holder.txtViewStatus.setText(getDfuStatus(record.result));
        return view;
    }
    static class ViewHolder
    {
        TextView txtViewMac;
        TextView txtModel;
        TextView txtDestVer;
        TextView txtVer;
        TextView txtViewStatus;
    }

    public static String getDfuStatus(int dfuStatus)
    {
        if (dfuStatus == CfgDFURecord.DFU_SUCCESS){
            return "Success";
        }
        else if (dfuStatus == CfgDFURecord.DFU_FAIL)
        {
            return "Failed";
        }
        else if (dfuStatus == CfgDFURecord.DFU_NOT_NEED)
        {
            return "Not DFU";
        }
        else
        {
            return String.valueOf(dfuStatus);
        }
    }
}
