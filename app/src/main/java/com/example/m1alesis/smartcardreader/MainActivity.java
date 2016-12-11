package com.example.m1alesis.smartcardreader;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    Button launchScanBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        launchScanBtn = (Button) findViewById(R.id.launchScan);
        launchScanBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.launchScan:
                Intent scannerIntent = new Intent(MainActivity.this, NFCScanner.class);
                startActivity(scannerIntent);
                break;
        }

    }
}