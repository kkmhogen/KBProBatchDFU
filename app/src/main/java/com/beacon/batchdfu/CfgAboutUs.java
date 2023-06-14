package com.beacon.batchdfu;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class CfgAboutUs extends AppBaseActivity {
    private final static String LOG_TAG = "CfgBeaconAboutUs";
    protected TextView mVersionView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_cfg_about);
        mVersionView = findViewById(R.id.textViewVersion);

        String currentVersion = "1.0";
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
            String strAppName = getString(R.string.app_name) + " " + packageInfo.versionName;
            mVersionView.setText(strAppName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Version is not found for this app" + e.getMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }
}
