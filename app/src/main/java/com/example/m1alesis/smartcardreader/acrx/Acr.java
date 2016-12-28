package com.example.m1alesis.smartcardreader.acrx;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.acs.audiojack.AudioJackReader;
import com.acs.audiojack.Result;
import com.example.m1alesis.smartcardreader.util.ApduCommand;

/**
 * Created by m1alesis on 05/12/2016.
 */
public class Acr {

    public String TAG = "acrx";
    private int acr3xStartAudioLevel = 0;
    public String firmwareVersion = "";
    public boolean isAcrStarted = false;
    private static final String SAMPLE_LOYALTY_CARD_AID = "F222222222";
    private static final String ELEMENT_FILE_ID = "0001";

    private int STATUS_SELECT_AID = 0;
    private int STATUS_SELECT_FILE = 1;
    private int STATUS_READ_DATA = 2;
    private int current_status = 0;

    int timeout = 5;
    AudioJackReader mReader;
    AudioManager mAudioManager;
    public ApduCommand apdu;

    AcrStatus acrCallback;

    private int cardType = AudioJackReader.PICC_CARD_TYPE_ISO14443_TYPE_A
            | AudioJackReader.PICC_CARD_TYPE_ISO14443_TYPE_B
            | AudioJackReader.PICC_CARD_TYPE_FELICA_212KBPS
            | AudioJackReader.PICC_CARD_TYPE_FELICA_424KBPS
            | AudioJackReader.PICC_CARD_TYPE_AUTO_RATS;

    public Acr(AudioManager mAudioManager, final AcrStatus acrCallback){
        this.mAudioManager = mAudioManager;
        this.acrCallback = acrCallback;

        apdu = new ApduCommand();
        mReader = new AudioJackReader(mAudioManager);

        mReader.setOnFirmwareVersionAvailableListener(new AudioJackReader.OnFirmwareVersionAvailableListener() {
            @Override
            public void onFirmwareVersionAvailable(AudioJackReader audioJackReader, String s) {
                Log.i(TAG, "firmwareversion : "+s);
                acrCallback.validAcrDeteced();
                firmwareVersion = s;
                powerOn();
            }
        });

        /*a call back for different things such as sleep complete, set sleep timeout complete etc. */
        mReader.setOnResultAvailableListener(new AudioJackReader.OnResultAvailableListener() {

            @Override
            public void onResultAvailable(AudioJackReader reader, Result result) {
                Log.i(TAG, "result callback : " + String.valueOf(result.getErrorCode()));
                if (result.getErrorCode() == 246) {
                    powerOn();
                }
            }
        });

        /* Set the PICC response APDU callback. */
        mReader.setOnPiccResponseApduAvailableListener(new AudioJackReader.OnPiccResponseApduAvailableListener() {

            @Override
            public void onPiccResponseApduAvailable(AudioJackReader reader, byte[] responseApdu) {
                String resultHex = ApduCommand.ByteArrayToHexString(responseApdu);
                Log.i(TAG, "APDU response ("+current_status+")" + resultHex);

                if(resultHex.equals("9000")) {
                    if (current_status == STATUS_SELECT_AID) {
                        if (mReader.piccPowerOn(timeout, cardType)) {
                            Log.i(TAG, "selecting file");
                            byte[] selFile = ApduCommand.HexStringToByteArray("00A40200020001");
                            current_status = STATUS_SELECT_FILE;
                            mReader.piccTransmit(timeout, selFile);
                        } else {
                            Log.i(TAG, "timed out..");
                        }
                    }else if(current_status == STATUS_SELECT_FILE) {
                        if (mReader.piccPowerOn(timeout, cardType)) {
                            Log.i(TAG, "reading binary data");
                            byte[] readBinary = ApduCommand.HexStringToByteArray("00B0000000");
                            current_status = STATUS_READ_DATA;
                            mReader.piccTransmit(timeout, readBinary);
                        } else {
                            Log.i(TAG, "timed out..");
                        }
                    }
                }
            }
        });
    }

    public void start(){
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

    public void powerOn(){
        if (mReader.piccPowerOn(timeout, cardType)) {
            Log.i(TAG, "poweron true");
            byte[] test = ApduCommand.HexStringToByteArray("00A4040005F222222222");
            /*Transmit the command to the reader with timeout: 5 sec*/
            mReader.piccTransmit(timeout, test);
        }else{
            Log.i(TAG, "poweron false");
            powerOn();
        }
    }

    public void stop(){
        Log.i(TAG, "stoping acr.");
        mReader.stop();
        isAcrStarted = false;

        System.out.println("acr3x restoring audio level: " + acr3xStartAudioLevel);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, acr3xStartAudioLevel, 0);
        System.out.println("acr3x set audio stream level: " + mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    public void setAudioMax(){
        acr3xStartAudioLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        System.out.println("acr3x start audio stream level: " + acr3xStartAudioLevel);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        System.out.println("acr3x set audio stream level: " + mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    public boolean isAcrStarted(){
        return isAcrStarted;
    }


    public interface AcrStatus{
        public void validAcrDeteced();
    }
}
