package com.example.m1alesis.smartcardreader;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.acs.audiojack.AudioJackReader;
import com.acs.audiojack.ReaderException;
import com.example.m1alesis.smartcardreader.acrx.Acr32;
import com.example.m1alesis.smartcardreader.util.ApduCommand;

/**
 * Created by m1alesis on 20/08/2017.
 */

public class ContactReaderV2 extends AppCompatActivity implements CardReader.ScanCallback, Acr32.AcrStatus, View.OnClickListener {
    /*notification*/
    NotificationManager mNotifyMgr;
    Notification notification;
    private static final int NOTIFICATION_ID = 123;

    private int acr3xStartAudioLevel = 0;
    public String firmwareVersion = "";

    AudioJackReader mReader;

    /*Acr*/
    private ContactReaderV2.MusicIntentReceiver myReceiver;
    private boolean isAcrConnected = false;
    private static final int REQUEST_AUDIO_RECORD = 1;
    private AudioManager mAudioManager;
    Acr32 acrx;

    /*Nfc chip*/
    public static final String TAG = "acr32-contactReader";
    public CardReader mCardReader;
    public LinearLayout linearLayout;
    public TextView tagDataInput;
    public TextView acrActiveMessage;
    public LinearLayout loadingMessage;

    Button readContact;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contactreader);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Ncoid reader connected.");
//        Intent resultIntent = new Intent(this, MainActivity.class);
//        PendingIntent resultPendingIntent = PendingIntent.getActivity(
//                this,
//                0,
//                resultIntent,
//                PendingIntent.FLAG_UPDATE_CURRENT);
//        mBuilder.setContentIntent(resultPendingIntent);
        notification = mBuilder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        myReceiver = new ContactReaderV2.MusicIntentReceiver();
        mAudioManager = (AudioManager) getSystemService(this.AUDIO_SERVICE);
        acrx = new Acr32(mAudioManager, this);

        mCardReader = new CardReader(this);
        linearLayout = (LinearLayout) findViewById(R.id.scanWrapper);
        tagDataInput = (TextView) findViewById(R.id.tagDataInput);
        acrActiveMessage = (TextView) findViewById(R.id.acrActiveMessage);
        loadingMessage = (LinearLayout) findViewById(R.id.loadingAnimation);

        readContact = (Button) findViewById(R.id.readContact);
        readContact.setOnClickListener(this);

    }

    public boolean checkRecordAudioPermission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            //use checkSelfPermission()
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED){
                return true;
            }else{
                Log.i("permissions-test", "permission not yet granted");

                if(shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    Snackbar snackbar = Snackbar
                            .make(linearLayout, "Permission is required for the card reader.", Snackbar.LENGTH_LONG);
                    snackbar.show();
                }

                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_RECORD);
                return false;
            }
        }

        /*permission should already be granted from the manifest*/
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause called");
        /*stop acr*/
        if(acrx.isAcrStarted()){
            acrx.stop();
        }
        /*unregister broadcast reciever*/
        unregisterReceiver(myReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume called");

        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(myReceiver, filter);
    }

    @Override
    public void onDataRecieved(final String recordData) {
        // This callback is run on a background thread, but updates to UI elements must be performed
        // on the UI thread.
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Snackbar snackbar = Snackbar
                        .make(linearLayout, recordData, Snackbar.LENGTH_LONG);
                snackbar.show();
                tagDataInput.setText(recordData);
            }
        });
    }

    @Override
    public void validAcrDeteced() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loadingMessage.setVisibility(View.GONE);
                acrActiveMessage.setVisibility(View.VISIBLE);
                mNotifyMgr.notify(NOTIFICATION_ID, notification);
            }
        });
    }


    @Override
    public void apduFileDataRecieved(final String data) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Snackbar snackbar = Snackbar
                        .make(linearLayout, data, Snackbar.LENGTH_LONG);
                snackbar.show();
                String dataSanitize = data.replaceAll("[^A-Za-z0-9 ]", "");
                tagDataInput.setText(dataSanitize);
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()){
            case R.id.readContact:
                acrx.start();
                break;
        }

    }

    private class MusicIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        Log.i(TAG, "Headset is unplugged");
                        acrx.stop();
                        acrActiveMessage.setVisibility(View.GONE);
                        mNotifyMgr.cancel(NOTIFICATION_ID);

                        break;
                    case 1:
                        Log.i(TAG, "Headset is plugged");
                        loadingMessage.setVisibility(View.VISIBLE);
                        Boolean hasPerm = checkRecordAudioPermission();
                        /*if we have permission and acr is already not started*/
                        if(hasPerm && !acrx.isAcrStarted()){
                            /*set the volume to max*/
                            //acrx.start();
                        }
                        break;
                    default:
                        Log.i(TAG, "I have no idea what the headset state is");
                }
            }
        }
    }
}
