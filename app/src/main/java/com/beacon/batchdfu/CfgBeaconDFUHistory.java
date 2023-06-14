package com.beacon.batchdfu;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.ListView;


import java.io.File;

import java.text.SimpleDateFormat;

import java.util.List;


public class CfgBeaconDFUHistory extends AppBaseActivity implements AbsListView.OnScrollListener
{
    private String LOG_TAG = "DfuDetailsActivity.";

    public LayoutInflater mInflater;

    public ListView mListView;

    public List<CfgDFURecord> mDfuRecordList;

    public CfgDfuAdapter mRecordAdapter;

    private static SimpleDateFormat mDFULogFileFmt = new SimpleDateFormat("yyyy&MM&dd HH&mm&ss");// 日志文件格式


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cfg_nb_dfu_list);

        mInflater = LayoutInflater.from(this);
        mListView = (ListView) findViewById(R.id.list_DFU);

        //read dfu data
        KBDfuPreference pref = KBDfuPreference.shareInstance(this);
        mDfuRecordList = pref.getDfuResultList();

        //adapter
        mRecordAdapter = new CfgDfuAdapter(this, mDfuRecordList);
        mListView.setOnScrollListener(this);
        mListView.setAdapter(mRecordAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.history_menu, menu);

        return true;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 是否触发按键为back键
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        }
        return true;
    }

    public String writeHistoryToString() {
        File appDir = getApplication().getFilesDir();
        if (appDir == null || mDfuRecordList.size() == 0) {
            return null;
        }

        final StringBuilder strBuilder = new StringBuilder(1024 * 20);
        String strWriteLine = "MAC," + "Model," + "CurrentVersion," + "DFUVersion," + "Status" + "\n";
        strBuilder.append(strWriteLine);

        for (int i = 0; i < mDfuRecordList.size(); i++) {
            CfgDFURecord historyData = mDfuRecordList.get(i);

            strBuilder.append(historyData.macAddress).append(",");
            strBuilder.append(historyData.model).append(",");
            strBuilder.append(historyData.ver).append(",");
            strBuilder.append(historyData.dfuVer).append(",");
            strBuilder.append(historyData.result).append("\n");
        }

        return strBuilder.toString();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menu_export)
        {
            String strHistoryContent = writeHistoryToString();
            if (strHistoryContent == null)
            {
                toastShow(getString(R.string.no_data_to_export));
                return true;
            }

            Intent intent = new Intent(Intent.ACTION_SEND);
            String strExportTitle = "DFU List";
            intent.putExtra(Intent.EXTRA_SUBJECT, strExportTitle);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_TEXT, strHistoryContent);
            startActivity(Intent.createChooser(intent, "send to email"));
            return true;
        }
        else if (item.getItemId() == R.id.menu_clear)
        {
            KBDfuPreference pref = KBDfuPreference.shareInstance(this);
            pref.clearDfuList();
            mDfuRecordList.clear();
            mRecordAdapter.notifyDataSetChanged();
            return true;
        }


        return super.onOptionsItemSelected(item);
    }
}