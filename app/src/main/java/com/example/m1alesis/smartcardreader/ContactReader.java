package com.example.m1alesis.smartcardreader;

import android.Manifest;
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
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.acs.audiojack.AudioJackReader;
import com.acs.audiojack.ReaderException;
import com.example.m1alesis.smartcardreader.EMVHelper.exception.CommunicationException;
import com.example.m1alesis.smartcardreader.EMVHelper.parser.EmvParser;
import com.example.m1alesis.smartcardreader.EMVHelper.parser.IProvider;
import com.example.m1alesis.smartcardreader.acrx.Acr;
import com.example.m1alesis.smartcardreader.util.ApduCommand;

/**
 * Created by m1alesis on 20/08/2017.
 */

public class ContactReader extends AppCompatActivity implements CardReader.ScanCallback, Acr.AcrStatus {

    private int acr3xStartAudioLevel = 0;
    public String firmwareVersion = "";

    AudioJackReader mReader;
    /*Acr*/
    private ContactReader.MusicIntentReceiver myReceiver;
    private static final int REQUEST_AUDIO_RECORD = 1;
    private AudioManager mAudioManager;
    public boolean isAcrStarted = false;

    /*Nfc chip*/
    public static final String TAG = "acrx";
    public CardReader mCardReader;
    public LinearLayout linearLayout;
    public TextView tagDataInput;
    public TextView acrActiveMessage;
    public LinearLayout loadingMessage;

    /*ICC*/
    int slotNum = 0;
    int action = AudioJackReader.CARD_WARM_RESET;
    int timeout = 10 * 1000;    /* 10 seconds. */
    byte[] atr = null;

    int preferredProtocols = AudioJackReader.PROTOCOL_T0
            | AudioJackReader.PROTOCOL_T1;
    int activeProtocol = 0;
    byte[] responseApdu = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contactreader);



        myReceiver = new ContactReader.MusicIntentReceiver();
        mAudioManager = (AudioManager) getSystemService(this.AUDIO_SERVICE);

        mReader = new AudioJackReader(mAudioManager);

        mCardReader = new CardReader(this);
        linearLayout = (LinearLayout) findViewById(R.id.scanWrapper);
        tagDataInput = (TextView) findViewById(R.id.tagDataInput);
        acrActiveMessage = (TextView) findViewById(R.id.acrActiveMessage);
        loadingMessage = (LinearLayout) findViewById(R.id.loadingAnimation);

        mReader.setOnFirmwareVersionAvailableListener(new AudioJackReader.OnFirmwareVersionAvailableListener() {
            @Override
            public void onFirmwareVersionAvailable(AudioJackReader audioJackReader, String s) {
                Log.i(TAG, "firmwareversion : "+s);
                firmwareVersion = s;
                validAcrDeteced();
                readContact();

            }
        });

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
        if(isAcrStarted()){
            stopContactReader();
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
            }
        });
    }





    public void readContact(){
        try {
            /* Reset the card. */
            atr = mReader.power(slotNum, action, timeout);
            Log.i(TAG, "ATR - "+ ApduCommand.ByteArrayToHexString(atr));
            if (atr != null) {
                /* Set the protocol. */
                activeProtocol = mReader.setProtocol(slotNum, preferredProtocols, timeout);

                //[Step 1] Select 1PAY.SYS.DDF01 to get the PSE directory
                Log.i(TAG, "[Step 1] Select 1PAY.SYS.DDF01 to get the PSE directory");
                byte[] command = ApduCommand.buildCommand("00A404000E315041592E5359532E4444463031", "");
                responseApdu = mReader.transmit(slotNum, command, timeout);
                String resultHex = ApduCommand.ByteArrayToHexString(responseApdu);
                Log.i(TAG, "the response is "+ resultHex);
                Log.i(TAG, "the sw1 is "+ getStatus(resultHex, "sw1"));
                Log.i(TAG, "the sw2 is "+ getStatus(resultHex, "sw2"));

                if(getStatus(resultHex, "sw1").equals("61")){
                    //[Step 2] Get response with correct Le
                    Log.i(TAG, "[Step 2] Get response with correct Le");
                    byte[] command2 = ApduCommand.buildCommand("00C000001C", "");
                    byte[] responseApdu2 = mReader.transmit(slotNum, command2, timeout);
                    Log.i(TAG, "the response is "+ responseApdu2);
                    String resultHex2 = ApduCommand.ByteArrayToHexString(responseApdu2);
                    Log.i(TAG, "the response is "+ resultHex2);
//                    Log.i(TAG, "the sw1 is "+ getStatus(resultHex2, "sw1"));
//                    Log.i(TAG, "the sw2 is "+ getStatus(resultHex2, "sw2"));
                }
            }

        } catch (ReaderException e) {
            e.getMessage();
            e.printStackTrace();
        }
    }

    public String getStatus(String status, String position) {
        String result = "";
        switch(position){
            case "sw1":
                result = status.substring(0, 2);
                break;
            case "sw2":
                result = status.substring(2, 4);
                break;
        }

        return result;
        //return str.length() < 2 ? str : str.substring(0, 2);
    }

    public String buildApdu(String apduCmd, String responseLength){
        String cmd = "";
        switch(apduCmd){
            //[Step 1] Select 1PAY.SYS.DDF01 to get the PSE directory
            case "pse":
                cmd = "00A404000E315041592E5359532E4444463031";
                break;
            case "response":
                cmd = "00C00000"+responseLength;
        }

        return cmd;
    }

    @Override
    public void apduFileDataRecieved(String data) {

    }

    public void startContactReader(){
        setAudioMax();
        mReader.start();
        isAcrStarted = true;
        mReader.reset(new AudioJackReader.OnResetCompleteListener() {
            @Override
            public void onResetComplete(AudioJackReader arg0) {
                Log.i(TAG, "reset has complete.");
                mReader.getFirmwareVersion();
            }
        });

        /*if in 5sec no firmware version is received, its not a valid acr*/
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //Do something after 100ms
                if (firmwareVersion.equals("")) {
                    mReader.getFirmwareVersion();
                    Log.i(TAG, "this is not valid acr ");
                    //mReader.stop();
                }
            }
        }, 3000);
    }

    public void stopContactReader(){
        Log.i(TAG, "stoping acr.");
        mReader.stop();
        isAcrStarted = false;
        System.out.println("acr3x restoring audio level: " + acr3xStartAudioLevel);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, acr3xStartAudioLevel, 0);
        System.out.println("acr3x set audio stream level: " + mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    public boolean isAcrStarted(){
        return isAcrStarted;
    }

    public void setAudioMax(){
        acr3xStartAudioLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        System.out.println("acr3x start audio stream level: " + acr3xStartAudioLevel);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        System.out.println("acr3x set audio stream level: " + mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }



    private class MusicIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                    case 0:
                        Log.i(TAG, "Headset is unplugged");
                        stopContactReader();
                        acrActiveMessage.setVisibility(View.GONE);

                        break;
                    case 1:
                        Log.i(TAG, "Headset is plugged");
                        loadingMessage.setVisibility(View.VISIBLE);
                        Boolean hasPerm = checkRecordAudioPermission();
                        /*if we have permission and acr is already not started*/
                        if(hasPerm){
                            /*set the volume to max*/
                            startContactReader();
                        }
                        break;
                    default:
                        Log.i(TAG, "I have no idea what the headset state is");
                }
            }
        }
    }
}
