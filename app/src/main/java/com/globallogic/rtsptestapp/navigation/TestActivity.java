package com.globallogic.rtsptestapp.navigation;

import android.Manifest;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.globallogic.rtsptestapp.R;
import com.mapbox.mapboxsdk.Mapbox;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class TestActivity extends AppCompatActivity {
    private static final String TAG = "TestActivity";
    private static final int PERMISSION_REQUEST_CODE = 329;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_test);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
        } else {
            Log.i(TAG, "onCreate: Permission ok");
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PERMISSION_GRANTED) {
            Log.i(TAG, "onCreate: Permission ok");
        }else {
            Log.e(TAG, "onRequestPermissionsResult: ERROR" );
            finish();
        }
    }
}
