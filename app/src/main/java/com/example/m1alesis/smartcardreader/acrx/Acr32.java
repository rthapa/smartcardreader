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
import com.acs.audiojack.ReaderException;
import com.acs.audiojack.Result;
import com.acs.audiojack.TrackData;
import com.example.m1alesis.smartcardreader.EMVHelper.exception.CommunicationException;
import com.example.m1alesis.smartcardreader.EMVHelper.model.EmvCard;
import com.example.m1alesis.smartcardreader.EMVHelper.parser.EmvParser;
import com.example.m1alesis.smartcardreader.EMVHelper.parser.IProvider;
import com.example.m1alesis.smartcardreader.util.ApduCommand;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Created by m1alesis on 05/12/2016.
 */
public class Acr32 implements IProvider {

    public String TAG = "acr32";
    private int acr3xStartAudioLevel = 0;
    public String firmwareVersion = "";
    public boolean isAcrStarted = false;

    private static final String SELECT_PSE = "00A404000E315041592E5359532E4444463031";
    private static final String GET_RESPONSE = "00C00000";

    /* "OK" status word sent in response to SELECT AID command (0x9000) */
    private static final byte[] SELECT_OK_SW = {(byte) 0x90, (byte) 0x00};

    private int STATUS_SELECT_AID = 0;
    private int STATUS_SELECT_FILE = 1;
    private int STATUS_READ_DATA = 2;
    private int current_status = 0;

    AudioJackReader mReader;
    AudioManager mAudioManager;
    public ApduCommand apdu;

    AcrStatus acrCallback;

    private int cardType = AudioJackReader.PICC_CARD_TYPE_ISO14443_TYPE_A
            | AudioJackReader.PICC_CARD_TYPE_ISO14443_TYPE_B
            | AudioJackReader.PICC_CARD_TYPE_FELICA_212KBPS
            | AudioJackReader.PICC_CARD_TYPE_FELICA_424KBPS
            | AudioJackReader.PICC_CARD_TYPE_AUTO_RATS;

    /*ICC*/
    int slotNum = 0;
    int action = AudioJackReader.CARD_WARM_RESET;
    int timeout = 20000;    /* 10 seconds. */
    byte[] atr = null;

    int preferredProtocols = AudioJackReader.PROTOCOL_T0
            | AudioJackReader.PROTOCOL_T1;
    int activeProtocol = 0;
    byte[] responseApdu = null;

    public Acr32(AudioManager mAudioManager, final AcrStatus acrCallback){
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
//                powerOn();
                readICC();
            }
        });

        /*a call back for different things such as sleep complete, set sleep timeout complete etc. */
        mReader.setOnResultAvailableListener(new AudioJackReader.OnResultAvailableListener() {

            @Override
            public void onResultAvailable(AudioJackReader reader, Result result) {
                Log.i(TAG, "result callback : " + String.valueOf(result.getErrorCode()));
//                if (result.getErrorCode() == 246) {
//                    powerOn();
//                }
            }
        });


        mReader.setOnTrackDataAvailableListener(new AudioJackReader.OnTrackDataAvailableListener(){

            @Override
            public void onTrackDataAvailable(AudioJackReader audioJackReader, TrackData trackData) {
                //Log.i();
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

    public void readICC(){
        try {
            /* Reset the card. */
            atr = mReader.power(slotNum, action, timeout);
            Log.i(TAG, "ATR - "+ ApduCommand.ByteArrayToHexString(atr));
            if (atr != null) {
                EmvParser emv = new EmvParser(this, false);
                EmvCard card = emv.readEmvCard();

                Log.i("card-detail", String.valueOf(card.getApplicationLabel()));
                Log.i("card-detail", String.valueOf(card.getAid()));
                Log.i("card-detail", String.valueOf(card.getAtrDescription()));

                //Log.i("card-detail", card.getHolderFirstname());
                //Log.i("card-detail", String.valueOf(card.getHolderLastname()));
                Log.i("card-detail", String.valueOf(card.getExpireDate()));
                Log.i("card-detail", card.getCardNumber());
                Log.i("card-detail", String.valueOf(card.getListTransactions()));

                Log.i("card-detail", String.valueOf(card.getLeftPinTry()));

            }
        } catch (ReaderException e) {
            e.getMessage();
            e.printStackTrace();
        } catch (CommunicationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] transceive(byte[] pCommand) throws CommunicationException {
        try {
            responseApdu = mReader.transmit(0, pCommand, timeout);
            Log.i("transieve-data : ", "command : "+toHexString(pCommand)+" || result : "+toHexString(responseApdu));
            return responseApdu;
        } catch (ReaderException e) {
            e.getMessage();
            e.printStackTrace();
        }

        return new byte[0];
    }


    public void powerOn(){

        new Thread(new Runnable() {

            @Override
            public void run() {

                    /* Transmit the APDU. */
                try {
                    atr = mReader.power(0, action, timeout);
                    Log.i(TAG, "ATR - "+ ApduCommand.ByteArrayToHexString(atr));
                    if (atr != null) {
                /* Set the protocol. */
                        activeProtocol = mReader.setProtocol(0, preferredProtocols, timeout);

                        //[Step 1] Select 1PAY.SYS.DDF01 to get the PSE directory
                        Log.i(TAG, "[Step 1] Select 1PAY.SYS.DDF01 to get the PSE directory");
                        byte[] command = toByteArray("00A404000E315041592E5359532E4444463031");
                        responseApdu = mReader.transmit(0, command, timeout);
                        String resultHex = toHexString(responseApdu);
                        Log.i(TAG, "the response is "+ resultHex);

                        if(getStatus(resultHex, "sw1").equals("61")){
                            //[Step 2] Get response with correct Le
                            Log.i(TAG, "[Step 2] Get response with correct Le");
                            command = toByteArray("00C00000"+getStatus(resultHex, "sw2"));
                            responseApdu = mReader.transmit(0, command, timeout);
                            resultHex = toHexString(responseApdu);
                            Log.i(TAG, "the response is "+ resultHex);

                            int resultLength = responseApdu.length;
                            byte[] statusWord = {responseApdu[resultLength-2], responseApdu[resultLength-1]};
                            if (Arrays.equals(SELECT_OK_SW, statusWord)) {
                                //[Step 3] Get response with correct Le
                                Log.i(TAG, "[Step 3] Send READ RECORD with 0 to find out where the record is");
                                //00B2014400
                                command = toByteArray("00B2010C00");
                                responseApdu = mReader.transmit(0, command, timeout);
                                resultHex = toHexString(responseApdu);
                                Log.i(TAG, "the response is "+ resultHex);

                                if(getStatus(resultHex, "sw1").equals("6C")){
                                    //[Step 4] Get response with correct Le
                                    Log.i(TAG, "[Step 4] Send READ RECORD with 1C to get the PSE data");
                                    command = toByteArray("00B2010C"+getStatus(resultHex, "sw2"));
                                    responseApdu = mReader.transmit(0, command, timeout);
                                    resultHex = toHexString(responseApdu);
                                    Log.i(TAG, "the response is "+ resultHex);

                                    resultLength = responseApdu.length;
                                    byte[] statusWord2 = {responseApdu[resultLength-2], responseApdu[resultLength-1]};

                                    if (Arrays.equals(SELECT_OK_SW, statusWord2)) {

                                    }
                                }

                            }






                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }


            }
        }).start();
//        try {
//            atr = mReader.power(slotNum, action, timeout);
//            Log.i(TAG, "ATR - "+ ApduCommand.ByteArrayToHexString(atr));
//            if (atr != null) {
//                /* Set the protocol. */
//                activeProtocol = mReader.setProtocol(slotNum, preferredProtocols, timeout);
//
//                //[Step 1] Select 1PAY.SYS.DDF01 to get the PSE directory
//                Log.i(TAG, "[Step 1] Select 1PAY.SYS.DDF01 to get the PSE directory");
//                byte[] command = ApduCommand.buildCommand("00A404000E315041592E5359532E4444463031", "");
//                responseApdu = mReader.transmit(slotNum, command, timeout);
//
//                String resultHex = ApduCommand.ByteArrayToHexString(responseApdu);
//                Log.i(TAG, "the response is "+ resultHex);
//                Log.i(TAG, "the sw1 is "+ getStatus(resultHex, "sw1"));
//                Log.i(TAG, "the sw2 is "+ getStatus(resultHex, "sw2"));
//
//                if(getStatus(resultHex, "sw1").equals("61")){
//                    //[Step 2] Get response with correct Le
//                    Log.i(TAG, "[Step 2] Get response with correct Le");
//                    byte[] command2 = ApduCommand.buildCommand("00C000001C", "");
//                    byte[] responseApdu2 = mReader.transmit(slotNum, command2, timeout);
//                    Log.i(TAG, "the response is "+ responseApdu2);
//                    String resultHex2 = ApduCommand.ByteArrayToHexString(responseApdu2);
//                    Log.i(TAG, "the response is "+ resultHex2);
//                }
//            }
//        } catch (ReaderException e) {
//            e.getMessage();
//            e.printStackTrace();
//        }
    }

    private String toHexString(byte[] buffer) {

        String bufferString = "";

        if (buffer != null) {

            for (int i = 0; i < buffer.length; i++) {

                String hexChar = Integer.toHexString(buffer[i] & 0xFF);
                if (hexChar.length() == 1) {
                    hexChar = "0" + hexChar;
                }

                bufferString += hexChar.toUpperCase(Locale.US) + " ";
            }
        }

        return bufferString;
    }

    private byte[] toByteArray(String hexString) {

        byte[] byteArray = null;
        int count = 0;
        char c = 0;
        int i = 0;

        boolean first = true;
        int length = 0;
        int value = 0;

        // Count number of hex characters
        for (i = 0; i < hexString.length(); i++) {

            c = hexString.charAt(i);
            if (c >= '0' && c <= '9' || c >= 'A' && c <= 'F' || c >= 'a'
                    && c <= 'f') {
                count++;
            }
        }

        byteArray = new byte[(count + 1) / 2];
        for (i = 0; i < hexString.length(); i++) {

            c = hexString.charAt(i);
            if (c >= '0' && c <= '9') {
                value = c - '0';
            } else if (c >= 'A' && c <= 'F') {
                value = c - 'A' + 10;
            } else if (c >= 'a' && c <= 'f') {
                value = c - 'a' + 10;
            } else {
                value = -1;
            }

            if (value >= 0) {

                if (first) {

                    byteArray[length] = (byte) (value << 4);

                } else {

                    byteArray[length] |= value;
                    length++;
                }

                first = !first;
            }
        }

        return byteArray;
    }

    public void stop(){
        Log.i(TAG, "stoping acr.");
        mReader.stop();
        isAcrStarted = false;

        System.out.println("acr3x restoring audio level: " + acr3xStartAudioLevel);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, acr3xStartAudioLevel, 0);
        System.out.println("acr3x set audio stream level: " + mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
    }

    public String getStatus(String status, String position) {
        String result = "";
        status = status.replaceAll("\\s+","");
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
        public void apduFileDataRecieved(String data);
    }
}
