package com.example.m1alesis.smartcardreader.EMVHelper.model;

import android.util.Log;

import com.example.m1alesis.smartcardreader.EMVHelper.enums.CommandEnum;
import com.example.m1alesis.smartcardreader.EMVHelper.model.enums.CurrencyEnum;
import com.example.m1alesis.smartcardreader.EMVHelper.utils.BytesUtils;
import com.example.m1alesis.smartcardreader.EMVHelper.utils.CommandApdu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by m1alesis on 29/10/2017.
 */

public class CVM extends AbstractData{
    /*
    * Byte 1, We only support these CVM
    * x x 0 0 0 0 0 1 Plain PIN by ICC
    * x x 0 0 0 0 1 0 Encrypted PIN online
    * x x 0 0 0 1 0 1 Enciphered PIN verification performed by ICC and signature (paper)
    * x x 0 1 1 1 1 0 Signature (paper)
    * x x 0 1 1 1 1 1 No CVM required
    * */


    // x x 0 0 0 0 0 1 Plain PIN by ICC
    public static final double CVM_PLAIN_PIN = 1;
    // x x 0 0 0 0 1 0 Encrypted PIN online
    public static final double CV_ENCRYPTED_PIN = 2;
    // x x 0 0 0 1 0 1 Enciphered PIN verification performed by ICC and signature (paper)
    public static final double CV_ENCRYPTED_PIN_AND_SIGNATURE = 5;
    // x x 0 1 1 1 1 0 Signature (paper)
    public static final double CV_SIGNATURE = 30;
    // x x 0 1 1 1 1 1 No CVM required
    public static final double CV_NO_CVM = 31;




    //String[] ArrayList = new String[] {"00","02","03","06", "07", "08", "09"};
    ArrayList<String> applicableCVRules = new ArrayList<>();
    /*CVMs that we must perform*/
    ArrayList<String> cvmToBePerformed = new ArrayList<>();

    public CVM(){

        /**
         *  Byte 2, We're not supporting cashback at this point. only applies to us if,
         * '00' Always
         * '02' If not unattended cash and not manual cash and not purchase with cashback
         * '03' If terminal supports the CVM
         * if currency code is nepalese rupees. For e.g currency code '826' = £. Can be found in AFL with tag '9F42'
         * '06' If transaction is in the application currency and is under X value
         * '07' If transaction is in the application currency and is over X value
         * '08' If transaction is in the application currency and is under Y value
         * '09' If transaction is in the application currency and is over Y value
         */
        applicableCVRules.add("00");
        applicableCVRules.add("02");
        applicableCVRules.add("03");
        applicableCVRules.add("06");
        applicableCVRules.add("07");
        applicableCVRules.add("08");
        applicableCVRules.add("09");
    }

    public void setCVMRule(final byte[] CVMByte, EmvCard card){
        //separate CVM Rules from CVM list
        byte[] CVRules = Arrays.copyOfRange(CVMByte, 8, CVMByte.length);

        Log.i("errorcheck-cvmlength",Integer.toString(CVRules.length)); //20
        Log.i("errorcheck-cvmlength",BytesUtils.toHexString(CVRules)); //20

        //convert to hex so its easy to compare
        String[] hexArray = BytesUtils.toHexArray(CVRules);

		/*check hexarray contains elements and its even length*/
        if(hexArray.length < 1 || (CVRules.length & 1) != 0){
            Log.i("errorcheck-cvm-error","CVM cannot be parsed"); //20
        }

        String cardCurrency = card.getCurrency();
        String nepCurrencyCode = Integer.toString(CurrencyEnum.NPR.getISOCodeNumeric());
        Log.i("errorcheck-card-curr", cardCurrency);
        Log.i("errorcheck-term-curr", nepCurrencyCode);
        cvmToBePerformed = new ArrayList<>();
        Boolean first = true;
        for (int i = 0; i < hexArray.length; i++) {
            //42
            if(first){
                String binary = hexToBinary(hexArray[i]);

                Log.i("errorcheck-binary-"+hexArray[i],binary); //20
                //add this rule to be performed list
                cvmToBePerformed.add(hexArray[i]);
                first = false;
            }else{
                //if this rule is applicable to us
                Boolean isApplicable = applicableCVRules.contains(hexArray[i]);
                //set applicable to false if currency does not match
                if(cardCurrency != nepCurrencyCode && (hexArray[i] == "06" || hexArray[i] == "07" || hexArray[i] == "08" || hexArray[i] == "09")){
                    isApplicable = false;
                }

                if(!isApplicable){
                    Log.i("errorcheck-cvm-error",hexArray[i]+" not applicable");
                    //remove the last added element from CVM to be performed
                    cvmToBePerformed.remove(cvmToBePerformed.size() - 1);
                }else{
                    cvmToBePerformed.add(hexArray[i]);
                }

                first = true;
            }
        }

        Log.i("errorcheck-cvmtoperform", String.valueOf(cvmToBePerformed));
    }

//    private boolean terminalSupportsCVRule(String hexString){
//        if(hexString == null || hexString.isEmpty() ){
//            return false;
//        }
//        List<Integer> bitPos = bitPositions(Integer.parseInt(hexString, 16 ));
//
//        //check for x x 0 0 0 0 0 1 Plain PIN by ICC ?????
//        boolean plainPinICC = true;
//        for (Integer position : bitPos) {
//            System.out.println(position);
//            if(position != 1 && position < 7){
//                plainPinICC = false;
//               break;
//            }
//        }
//
//        // x x 0 1 1 1 1 0 Signature (paper) ?????
//        boolean signaturePaper = true;
//        for (Integer position : bitPos) {
//            System.out.println(position);
//            if(position == 1 || position == 3 && position != 4 && position != 5){
//                signaturePaper = false;
//                break;
//            }
//        }
//
//    }

    private static List<Integer> bitPositions(int number) {
        List<Integer> positions = new ArrayList<>();
        int position = 1;
        while (number != 0) {
            if ((number & 1) != 0) {
                positions.add(position);
            }
            position++;
            number = number >>> 1;
        }
        return positions;
    }

    String hexToBinary(String hex) {
        int i = Integer.parseInt(hex, 16);
        String bin = Integer.toBinaryString(i);
        return bin;
    }
}
