package com.example.m1alesis.smartcardreader.acrx;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import java.security.GeneralSecurityException;


import com.acs.audiojack.AesTrackData;
import com.acs.audiojack.AudioJackReader;
import com.acs.audiojack.DukptReceiver;
import com.acs.audiojack.DukptTrackData;
import com.acs.audiojack.Result;
import com.acs.audiojack.Track1Data;
import com.acs.audiojack.Track2Data;
import com.acs.audiojack.TrackData;
import com.example.m1alesis.smartcardreader.util.ApduCommand;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by m1alesis on 05/12/2016.
 */
public class Acr {

    private byte[] mAesKey = new byte[16];
    private byte[] mIksn = new byte[10];
    public static final String DEFAULT_AES_KEY_STRING = "4E 61 74 68 61 6E 2E 4C 69 20 54 65 64 64 79 20";

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
                if(current_status == STATUS_READ_DATA){
                    try {
                        String fileData = new String(responseApdu, "UTF-8");
                        Log.i(TAG, "data found : "+fileData);
                        acrCallback.apduFileDataRecieved(fileData);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }

                if(resultHex.equals("9000")) {
                    if (current_status == STATUS_SELECT_AID) {
                        Log.i(TAG, "selecting file");
                        byte[] selFile = ApduCommand.buildCommand(apdu.instructionArr.get("select_file"), ELEMENT_FILE_ID);
                        current_status = STATUS_SELECT_FILE;
                        mReader.piccTransmit(timeout, selFile);
                    }else if(current_status == STATUS_SELECT_FILE) {
                        Log.i(TAG, "reading binary data");
                        byte[] readBinary = ApduCommand.buildCommand(apdu.instructionArr.get("read_binary"), "");
                        current_status = STATUS_READ_DATA;
                        mReader.piccTransmit(timeout, readBinary);
                    }
                }


                /* Power off the PICC. */
                //mReader.piccPowerOff();
            }
        });

        Log.i("track-data-test", "onlistenders added");

        mReader.setOnTrackDataNotificationListener(new AudioJackReader.OnTrackDataNotificationListener() {
            @Override
            public void onTrackDataNotification(AudioJackReader audioJackReader) {
                Log.i("track-data-test","track data notification listener triggered");
            }
        });

        mReader.setOnAuthCompleteListener(new AudioJackReader.OnAuthCompleteListener() {
            @Override
            public void onAuthComplete(AudioJackReader audioJackReader, int errorCode) {
                if (errorCode == AudioJackReader.AUTH_ERROR_SUCCESS) {
                    Log.i("track-data-test","reader auth success");
                }else{
                    Log.i("track-data-test","reader auth error");
                }
            }
        });

        mReader.setOnTrackDataAvailableListener(new AudioJackReader.OnTrackDataAvailableListener() {
            private Track1Data mTrack1Data;
            private Track2Data mTrack2Data;
            private Track1Data mTrack1MaskedData;
            private Track2Data mTrack2MaskedData;
            private String mTrack1MacString;
            private String mTrack2MacString;
            private String mBatteryStatusString;
            private String mKeySerialNumberString;
            private int mErrorId;
            private DukptReceiver mDukptReceiver = new DukptReceiver();
            private byte[] mIpek = new byte[16];

            @Override
            public void onTrackDataAvailable(AudioJackReader audioJackReader, TrackData trackData) {
                mTrack1Data = new Track1Data();
                mTrack2Data = new Track2Data();
                mTrack1MaskedData = new Track1Data();
                mTrack2MaskedData = new Track2Data();
                mTrack1MacString = "";
                mTrack2MacString = "";

                Log.i("track-data-test","track data is now available");
                if ((trackData.getTrack1ErrorCode() != TrackData.TRACK_ERROR_SUCCESS)
                        && (trackData.getTrack2ErrorCode() != TrackData.TRACK_ERROR_SUCCESS)) {
                    Log.i("track-data-test","track data corrupted");
                } else if (trackData.getTrack1ErrorCode() != TrackData.TRACK_ERROR_SUCCESS) {
                    Log.i("track-data-test","track1 data corrupted");
                } else if (trackData.getTrack2ErrorCode() != TrackData.TRACK_ERROR_SUCCESS) {
                    Log.i("track-data-test","track2 data corrupted");
                }

                /* Show the track error. */
                if ((trackData.getTrack1ErrorCode() != TrackData.TRACK_ERROR_SUCCESS)
                        || (trackData.getTrack2ErrorCode() != TrackData.TRACK_ERROR_SUCCESS)) {
                    Log.i("track-data-test","track data error.");
                }

                /* Show the track data. */
                if (trackData instanceof AesTrackData) {
                    Log.i("track-data-test","its aestrack");
                    showAesTrackData((AesTrackData) trackData);
                } else if (trackData instanceof DukptTrackData) {
                    Log.i("track-data-test","its dukpt");
                    showDukptTrackData((DukptTrackData) trackData);
                }else{
                    Log.i("track-data-test","its nothing");
                }
            }

            private void showAesTrackData(AesTrackData trackData) {

                byte[] decryptedTrackData = null;

            /* Decrypt the track data. */
                try {

                    decryptedTrackData = aesDecrypt(mAesKey,
                            trackData.getTrackData());

                } catch (GeneralSecurityException e) {

                    //track data error on decryption
                    Log.i("track-data-test","error on decryp aes");
                    return;
                }

            /* Verify the track data. */
                if (!mReader.verifyData(decryptedTrackData)) {

                    //track data error on checksum
                    Log.i("track-data-test","error on checksum aes");
                    return;
                }

            /* Decode the track data. */
                mTrack1Data.fromByteArray(decryptedTrackData, 0,
                        trackData.getTrack1Length());
                mTrack2Data.fromByteArray(decryptedTrackData, 79,
                        trackData.getTrack2Length());

                 /* Show the track data. */
                showTrackData();
            }

            /**
             * Shows the DUKPT track data.
             *
             * @param trackData
             *            the DUKPT track data.
             */
            private void showDukptTrackData(DukptTrackData trackData) {

                int ec = 0;
                int ec2 = 0;
                byte[] track1Data = null;
                byte[] track2Data = null;
                String track1DataString = null;
                String track2DataString = null;
                byte[] key = null;
                byte[] dek = null;
                byte[] macKey = null;
                byte[] dek3des = null;

                mKeySerialNumberString = toHexString(trackData.getKeySerialNumber());
                mTrack1MacString = toHexString(trackData.getTrack1Mac());
                mTrack2MacString = toHexString(trackData.getTrack2Mac());
                mTrack1MaskedData.fromString(trackData.getTrack1MaskedData());
                mTrack2MaskedData.fromString(trackData.getTrack2MaskedData());

                /* Compare the key serial number. */
                if (!DukptReceiver.compareKeySerialNumber(mIksn,
                        trackData.getKeySerialNumber())) {

                    //compare key serialnumber error
                    Log.i("track-data-test","track data error on compare key serial number");
                    return;
                }

                /* Get the encryption counter from KSN. */
                ec = DukptReceiver.getEncryptionCounter(trackData
                        .getKeySerialNumber());

                /* Get the encryption counter from DUKPT receiver. */
                ec2 = mDukptReceiver.getEncryptionCounter();

                /*
                 * Load the initial key if the encryption counter from KSN is less
                 * than the encryption counter from DUKPT receiver.
                 */
                if (ec < ec2) {

                    mDukptReceiver.loadInitialKey(mIpek);
                    ec2 = mDukptReceiver.getEncryptionCounter();
                }

                /*
                 * Synchronize the key if the encryption counter from KSN is greater
                 * than the encryption counter from DUKPT receiver.
                 */
                while (ec > ec2) {

                    mDukptReceiver.getKey();
                    ec2 = mDukptReceiver.getEncryptionCounter();
                }

                if (ec != ec2) {

                    //track data error
                    Log.i("track-data-test","track data error ec != ec2");
                    return;
                }

                key = mDukptReceiver.getKey();
                if (key == null) {

                    //maximum encryption count has been reached.
                    Log.i("track-data-test","track data max encryption count reached");
                    return;
                }

                dek = DukptReceiver.generateDataEncryptionRequestKey(key);
                macKey = DukptReceiver.generateMacRequestKey(key);
                dek3des = new byte[24];

                /* Generate 3DES key (K1 = K3) */
                System.arraycopy(dek, 0, dek3des, 0, dek.length);
                System.arraycopy(dek, 0, dek3des, 16, 8);

                try {

                    if (trackData.getTrack1Data() != null) {

                    /* Decrypt the track 1 data. */
                        track1Data = tripleDesDecrypt(dek3des,
                                trackData.getTrack1Data());

                    /* Generate the MAC for track 1 data. */
                        mTrack1MacString += " ("
                                + toHexString(DukptReceiver.generateMac(macKey,
                                track1Data)) + ")";

                    /* Get the track 1 data as string. */
                        track1DataString = new String(track1Data, 1,
                                trackData.getTrack1Length(), "US-ASCII");

                    /* Divide the track 1 data into fields. */
                        mTrack1Data.fromString(track1DataString);
                    }

                    if (trackData.getTrack2Data() != null) {

                    /* Decrypt the track 2 data. */
                        track2Data = tripleDesDecrypt(dek3des,
                                trackData.getTrack2Data());

                    /* Generate the MAC for track 2 data. */
                        mTrack2MacString += " ("
                                + toHexString(DukptReceiver.generateMac(macKey,
                                track2Data)) + ")";

                    /* Get the track 2 data as string. */
                        track2DataString = new String(track2Data, 1,
                                trackData.getTrack2Length(), "US-ASCII");

                    /* Divide the track 2 data into fields. */
                        mTrack2Data.fromString(track2DataString);
                    }

                } catch (GeneralSecurityException e) {

                    //error on decryption.
                    Log.i("track-data-test","error on decryption");
                } catch (UnsupportedEncodingException e) {
                    Log.i("track-data-test","unsupported encoding track data");
                }

            /* Show the track data. */
                showTrackData();
            }

            /**
             * Shows the track data.
             */
            private void showTrackData() {
                //pan
                Log.i("track-data-test-pan",mTrack1MaskedData.getPrimaryAccountNumber());
                //name
                Log.i("track-data-test-name",mTrack1Data.getName());
                //track1 expiry
                Log.i("track-data-test-expiry",mTrack1Data.getExpirationDate());
                //service code
                Log.i("track-data-test-sc",mTrack1Data.getServiceCode());
                //discretionary data
                Log.i("track-data-test-descr",mTrack1Data.getDiscretionaryData());

                //t2 PAN
                Log.i("track-data-test-pan",mTrack2Data.getPrimaryAccountNumber());
                //t2 expiry date
                Log.i("track-data-test-expiry",mTrack2Data.getExpirationDate());
                //t2 service code
                Log.i("track-data-test-sc",mTrack2Data.getServiceCode());

                //t2 discretionary data
                Log.i("track-data-test-descr",mTrack2Data.getDiscretionaryData());

            }

            /**
             * Decrypts the data using Triple DES.
             *
             * @param key
             *            the key.
             * @param input
             *            the input buffer.
             * @return the output buffer.
             * @throws GeneralSecurityException
             *             if there is an error in the decryption process.
             */
            private byte[] tripleDesDecrypt(byte[] key, byte[] input)
                    throws GeneralSecurityException {

                SecretKeySpec secretKeySpec = new SecretKeySpec(key, "DESede");
                Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
                IvParameterSpec ivParameterSpec = new IvParameterSpec(new byte[8]);

                cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
                return cipher.doFinal(input);
            }

            /**
             * Converts the byte array to HEX string.
             *
             * @param buffer
             *            the buffer.
             * @return the HEX string.
             */
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

        });
    }


    /**
     * Decrypts the data using AES.
     *
     * @param key
     *            the key.
     * @param input
     *            the input buffer.
     * @return the output buffer.
     * @throws GeneralSecurityException
     *             if there is an error in the decryption process.
     */
    private byte[] aesDecrypt(byte key[], byte[] input)
            throws GeneralSecurityException {

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(new byte[16]);

        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

        return cipher.doFinal(input);
    }

    public void start(){
        setAudioMax();
        mReader.start();
        isAcrStarted = true;

//        if (!mReader.setAesKey(mAesKey)) {
//            Log.i(TAG, "aes key set failed");
//        } else {
//
//        }
        toByteArray(DEFAULT_AES_KEY_STRING, mAesKey);
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

    /**
     * Converts the HEX string to byte array.
     *
     * @param hexString
     *            the HEX string.
     * @return the number of bytes.
     */
    private int toByteArray(String hexString, byte[] byteArray) {

        char c = 0;
        boolean first = true;
        int length = 0;
        int value = 0;
        int i = 0;

        for (i = 0; i < hexString.length(); i++) {

            c = hexString.charAt(i);
            if ((c >= '0') && (c <= '9')) {
                value = c - '0';
            } else if ((c >= 'A') && (c <= 'F')) {
                value = c - 'A' + 10;
            } else if ((c >= 'a') && (c <= 'f')) {
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

            if (length >= byteArray.length) {
                break;
            }
        }

        return length;
    }

    public void powerOn(){
        if (mReader.piccPowerOn(timeout, cardType)) {
            Log.i(TAG, "poweron true");
            byte[] selectAid = ApduCommand.buildCommand(apdu.instructionArr.get("select_application"), SAMPLE_LOYALTY_CARD_AID);
            /*Transmit the command to the reader with timeout: 5 sec*/
            mReader.piccTransmit(timeout, selectAid);
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
        public void apduFileDataRecieved(String data);
    }
}
