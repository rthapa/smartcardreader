package com.example.m1alesis.smartcardreader;

import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.util.Log;

import com.example.m1alesis.smartcardreader.util.ApduCommand;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * Created by m1alesis on 21/11/2016.
 */
public class CardReader implements NfcAdapter.ReaderCallback{
    private static final String TAG = "CardReader";

    /* AID for our card service. */
    private static final String SAMPLE_LOYALTY_CARD_AID = "F222222222";
    /*File ID if we need to fetch from actual card*/
    private static final String ELEMENT_FILE_ID = "0001";

    /* "OK" status word sent in response to SELECT AID command (0x9000) */
    private static final byte[] SELECT_OK_SW = {(byte) 0x90, (byte) 0x00};

    /**
     *  Weak reference to prevent retain loop. mAccountCallback is responsible for exiting
     * foreground mode before it becomes invalid (e.g. during onPause() or onStop()).
     */
    private WeakReference<ScanCallback> mScanCallback;

    public ApduCommand apdu;

    public interface ScanCallback {
        public void onDataRecieved(String account);
    }

    public CardReader(ScanCallback accountCallback) {
        apdu = new ApduCommand();
        mScanCallback = new WeakReference<ScanCallback>(accountCallback);
    }

    /**
     * Callback when a new tag is discovered by the system.
     *
     * <p>Communication with the card should take place here.
     *
     * @param tag Discovered tag
     */
    @Override
    public void onTagDiscovered(Tag tag) {
        Log.i(TAG, "New tag discovered");

        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep != null) {
            try {
                /* Connect to the remote NFC device */
                isoDep.connect();

                /* Build SELECT AID command for our loyalty card service. */
                Log.i(TAG, "Requesting remote AID: " + SAMPLE_LOYALTY_CARD_AID);
                byte[] command = ApduCommand.buildCommand(apdu.instructionArr.get("select_application") ,SAMPLE_LOYALTY_CARD_AID);

                /* Send command to remote device */
                Log.i(TAG, "Sending: " + ApduCommand.ByteArrayToHexString(command));
                byte[] result = isoDep.transceive(command);

                /**
                 * If AID is successfully selected, 0x9000 is returned as the status word (last 2
                 * bytes of the result) by convention. Everything before the status word is
                 * optional payload, which is used here to hold the account number.
                 */
                String resultHex = ApduCommand.ByteArrayToHexString(result);

                /*If the status is only 0x9000, its a card with a valid AID therefore we select file manually*/
                if(resultHex.equals("9000")){
                    /*Select the file with iso file ID*/
                    byte[] selecyByFileCommand = ApduCommand.buildCommand(apdu.instructionArr.get("select_file"), ELEMENT_FILE_ID);
                    byte[] fileResult = isoDep.transceive(selecyByFileCommand);
                    String fileResultHex = ApduCommand.ByteArrayToHexString(fileResult);

                    /*If the returned status is 0x9000, we've found our file*/
                    if(fileResultHex.equals("9000")){
                        byte[] readBinaryCommand = ApduCommand.buildCommand(apdu.instructionArr.get("read_binary"), "");
                        result = isoDep.transceive(readBinaryCommand);
                    }
                }else{
                    /*We're dealing with emulated device*/
                    /*TODO : We're hoping to get result as data bytes with 0x9000 as status, check if this is not recieved*/
                }
                int resultLength = result.length;
                Log.i("status-len", String.valueOf(result.length));
                byte[] statusWord = {result[resultLength-2], result[resultLength-1]};
                String status = ApduCommand.ByteArrayToHexString(statusWord);
                Log.i("status",status);
                byte[] payload = Arrays.copyOf(result, resultLength - 2);
                if (Arrays.equals(SELECT_OK_SW, statusWord)) {
                    /*Convert to string*/
                    String fileData = new String(payload, "UTF-8");
                    Log.i(TAG, "Received: " + fileData);
                    /*Inform CardReader*/
                    mScanCallback.get().onDataRecieved(fileData);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error communicating with card: " + e.toString());
            }
        }
    }

}
