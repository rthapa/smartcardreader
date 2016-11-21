package com.example.m1alesis.smartcardreader;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by m1alesis on 21/11/2016.
 */
public class NFCScanner extends AppCompatActivity implements CardReader.ScanCallback  {

    public static final String TAG = "CardReaderActivity";
    public static int READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

    public CardReader mCardReader;
    public LinearLayout linearLayout;
    public TextView tagDataInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfcscanner);

        mCardReader = new CardReader(this);
        linearLayout = (LinearLayout) findViewById(R.id.scanWrapper);
        tagDataInput = (TextView) findViewById(R.id.tagDataInput);

        // Disable Android Beam and register our card reader callback
        enableReaderMode();
    }

    @Override
    public void onPause() {
        super.onPause();
        disableReaderMode();
    }

    @Override
    public void onResume() {
        super.onResume();
        enableReaderMode();
    }

    private void enableReaderMode() {
        Log.i(TAG, "Enabling reader mode");
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc != null) {
            nfc.enableReaderMode(this, mCardReader, READER_FLAGS, null);
        }
    }

    private void disableReaderMode() {
        Log.i(TAG, "Disabling reader mode");
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc != null) {
            nfc.disableReaderMode(this);

        }
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


}
