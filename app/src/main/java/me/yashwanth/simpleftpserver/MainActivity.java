package me.yashwanth.simpleftpserver;


import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
    TextView tvStatus,tvDetails;
    Boolean isRunning = false;
    Button btnChange;
    boolean storagePermissionGranted = false;
    private static String TAG = MainActivity.class.getSimpleName();
    private View mLayout;

    BroadcastReceiver mFsActionsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction() == FTPService.ACTION_FAILEDTOSTART){
                Toast.makeText(context,intent.getStringExtra("reason"),Toast.LENGTH_LONG).show();
            }
            else {
                updateRunningState(intent);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnChange = (Button) findViewById(R.id.btnChange);
        tvStatus = (TextView)findViewById(R.id.statusText);
        tvDetails = (TextView) findViewById(R.id.detailsText);

        btnChange.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if(storagePermissionGranted){
                    if(isRunning){
                        stopServer();
                    }
                    else{
                        startServer();
                    }
                }
            }
        });
        if(checkStoragePermission()){
            storagePermissionGranted = true;

        }else{
            requestStoragePermission();
        }
    }
    @Override
    protected void onPause() {
        super.onPause();

        Log.v(TAG, "onPause: Unregistering the FTPServer actions");
        unregisterReceiver(mFsActionsReceiver);

    }
    @Override
    protected void onResume() {
        super.onResume();

        updateRunningState(null);
        boolean stat = checkStoragePermission();
        Log.d(TAG, "onResume: Registering the FTP server actions");
        IntentFilter filter = new IntentFilter();
        filter.addAction(FTPService.ACTION_STARTED);
        filter.addAction(FTPService.ACTION_STOPPED);
        filter.addAction(FTPService.ACTION_FAILEDTOSTART);
        registerReceiver(mFsActionsReceiver, filter);
    }
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        if (requestCode == 1) {
            storagePermissionGranted = true;

        }else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
    private void startServer() {
        sendBroadcast(new Intent(FTPService.ACTION_START_FTPSERVER));
    }

    private void stopServer() {
        sendBroadcast(new Intent(FTPService.ACTION_STOP_FTPSERVER));
    }

    private void updateRunningState(Intent i) {
        if(FTPService.isRunning()){
            tvStatus.setText("Running");
            tvDetails.setText("IP:"+FTPService.getLocalInetAddress(getBaseContext())+"\nPORT:"+FTPService.getPort());
            isRunning = true;
        }
        else{
            tvStatus.setText("Stopped");
            tvDetails.setText("");
            isRunning = false;
        }
    }
    public boolean checkStoragePermission() {

        // Verify that all required contact permissions have been granted.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }
    private void requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {

            Log.d("A","rationale");
        }else{
            // Camera permission has not been granted yet. Request it directly.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        // END_INCLUDE(camera_permission_request)
            }
    }

}

