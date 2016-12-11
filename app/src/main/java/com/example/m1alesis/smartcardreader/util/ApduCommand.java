package com.example.m1alesis.smartcardreader.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Rabi Thapa on 17/11/2016.
 */

public class ApduCommand {
    private String classStr, instructionStr, param1Str, param2Str;
    private String aid = "F222222222";

    /*APDU instructions*/
    public Map<String, String> instructionArr = new HashMap<String, String>();

    public ApduCommand(){
        /*Fill Instructions Array*/
        instructionArr.put("select_application", "00A40400");
        instructionArr.put("select_file", "00A40200");
        instructionArr.put("read_binary", "00B00000");
    }

    public static byte[] buildCommand(String command, String data){
        return HexStringToByteArray(command + String.format("%02X", data.length() / 2) + data);
    }

    /**
     * Utility class to convert a byte array to a hexadecimal string.
     *
     * @param bytes Bytes to convert
     * @return String, containing hexadecimal representation.
     */
    public static String ByteArrayToHexString(byte[] bytes) {
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Utility class to convert a hexadecimal string to a byte string.
     *
     * <p>Behavior with input strings containing non-hexadecimal characters is undefined.
     *
     * @param s String containing hexadecimal characters to convert
     * @return Byte array generated from input
     */
    public static byte[] HexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }


}
