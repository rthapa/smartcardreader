package com.example.m1alesis.smartcardreader.EMVHelper.parser;

import android.util.Log;

import com.example.m1alesis.smartcardreader.EMVHelper.enums.CommandEnum;
import com.example.m1alesis.smartcardreader.EMVHelper.enums.EmvCardScheme;
import com.example.m1alesis.smartcardreader.EMVHelper.enums.SwEnum;
import com.example.m1alesis.smartcardreader.EMVHelper.exception.CommunicationException;
import com.example.m1alesis.smartcardreader.EMVHelper.iso7816emv.EmvTags;
import com.example.m1alesis.smartcardreader.EMVHelper.iso7816emv.EmvTerminal;
import com.example.m1alesis.smartcardreader.EMVHelper.iso7816emv.TLV;
import com.example.m1alesis.smartcardreader.EMVHelper.iso7816emv.TagAndLength;
import com.example.m1alesis.smartcardreader.EMVHelper.model.Afl;
import com.example.m1alesis.smartcardreader.EMVHelper.model.CVM;
import com.example.m1alesis.smartcardreader.EMVHelper.model.EmvCard;
import com.example.m1alesis.smartcardreader.EMVHelper.model.EmvTransactionRecord;
import com.example.m1alesis.smartcardreader.EMVHelper.model.enums.CurrencyEnum;
import com.example.m1alesis.smartcardreader.EMVHelper.utils.CommandApdu;
import com.example.m1alesis.smartcardreader.EMVHelper.utils.ResponseUtils;
import com.example.m1alesis.smartcardreader.EMVHelper.utils.TlvUtil;
import com.example.m1alesis.smartcardreader.EMVHelper.utils.TrackUtils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import fr.devnied.bitlib.BytesUtils;

/**
 * Emv Parser.<br/>
 * Class used to read and parse EMV card
 */
public class EmvParser {

	/**
	 * Class Logger
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(EmvParser.class);

	/**
	 * PPSE directory "2PAY.SYS.DDF01"
	 */
	private static final byte[] PPSE = "2PAY.SYS.DDF01".getBytes();

	/**
	 * PSE directory "1PAY.SYS.DDF01"
	 */
	private static final byte[] PSE = "1PAY.SYS.DDF01".getBytes();

	/**
	 * Unknow response
	 */
	public static final int UNKNOW = -1;

	/**
	 * Card holder name separator
	 */
	public static final String CARD_HOLDER_NAME_SEPARATOR = "/";

	/**
	 * Provider
	 */
	private IProvider provider;

	/**
	 * use contact less mode
	 */
	private boolean contactLess;

	/**
	 * Card data
	 */
	private EmvCard card;

	/**
	 * CVM obj
	 */
	private CVM cvm;

	/**
	 * Constructor
	 *
	 * @param pProvider
	 *            provider to launch command
	 * @param pContactLess
	 *            boolean to indicate if the EMV card is contact less or not
	 */
	public EmvParser(final IProvider pProvider, final boolean pContactLess) {
		provider = pProvider;
		contactLess = pContactLess;
		card = new EmvCard();
		cvm = new CVM();
	}

	/**
	 * Method used to read public data from EMV card
	 *
	 * @return data read from card or null if any provider match the card type
	 */
	public EmvCard readEmvCard() throws CommunicationException {
		// use PSE first
		if (!readWithPSE()) {
			// Find with AID
			readWithAID();
		}
		return card;
	}

	/**
	 * Method used to select payment environment PSE or PPSE
	 *
	 * @return response byte array
	 * @throws CommunicationException
	 */
	protected byte[] selectPaymentEnvironment() throws CommunicationException {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Select " + (contactLess ? "PPSE" : "PSE") + " Application");
		}
		// Select the PPSE or PSE directory
		return provider.transceive(new CommandApdu(CommandEnum.SELECT, contactLess ? PPSE : PSE, 0).toBytes());
	}

	protected byte[] getResponse(byte[] apdu) throws CommunicationException {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Get response.. calling");
		}
		// Select the PPSE or PSE directory
		return provider.transceive(apdu);
	}

	/**
	 * Method used to get the number of pin try left
	 *
	 * @return the number of pin try left
	 * @throws CommunicationException
	 */
	protected int getLeftPinTry() throws CommunicationException {
		int ret = UNKNOW;
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Get Left PIN try");
		}
		// Left PIN try command
		byte[] data = provider.transceive(new CommandApdu(CommandEnum.GET_DATA, 0x9F, 0x17, 0).toBytes());
		if (ResponseUtils.isSucceed(data) || ResponseUtils.isEquals(data, SwEnum.SW_6C) ) {

			if(ResponseUtils.isEquals(data, SwEnum.SW_6C) ){
				//try with correct length
				String resultHex = toHexString(data);
				//data = getResponse(toByteArray("00C00000"+getStatus(resultHex, "sw2")));
				data = provider.transceive(new CommandApdu(CommandEnum.GET_DATA, 0x9F, 0x17, Integer.parseInt(getStatus(resultHex, "sw2"))).toBytes());
			}

			// Extract PIN try counter
			byte[] val = TlvUtil.getValue(data, EmvTags.PIN_TRY_COUNTER);
			if (val != null) {
				ret = BytesUtils.byteArrayToInt(val);
			}
		}
		return ret;
	}

	/**
	 * Method used to parse FCI Proprietary Template
	 *
	 * @param pData
	 *            data to parse
	 * @return
	 * @throws CommunicationException
	 */
	protected byte[] parseFCIProprietaryTemplate(final byte[] pData) throws CommunicationException {
		// Get SFI
		byte[] data = TlvUtil.getValue(pData, EmvTags.SFI);

		// Check SFI
		if (data != null) {
			int sfi = BytesUtils.byteArrayToInt(data);
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("SFI found:" + sfi);
			}
			data = provider.transceive(new CommandApdu(CommandEnum.READ_RECORD, sfi, sfi << 3 | 4, 0).toBytes());
			// If LE is not correct
			if (ResponseUtils.isEquals(data, SwEnum.SW_6C)) {
				data = provider.transceive(new CommandApdu(CommandEnum.READ_RECORD, sfi, sfi << 3 | 4, data[data.length - 1]).toBytes());
				Log.i("errorcheck", toHexString(data));
			}
			return data;
		}
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("(FCI) Issuer Discretionary Data is already present");
		}
		return pData;
	}

	/**
	 * Method used to extract application label
	 *
	 * @return decoded application label or null
	 */
	protected String extractApplicationLabel(final byte[] pData) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Extract Application label");
		}
		String label = null;
		byte[] labelByte = TlvUtil.getValue(pData, EmvTags.APPLICATION_LABEL);
		if (labelByte != null) {
			label = new String(labelByte);
		}
		return label;
	}

	/**
	 * Read EMV card with Payment System Environment or Proximity Payment System
	 * Environment
	 *
	 * @return true is succeed false otherwise
	 */
	protected boolean readWithPSE() throws CommunicationException {
		boolean ret = false;
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Try to read card with Payment System Environment");
		}
		// Select the PPSE or PSE directory
		byte[] data = selectPaymentEnvironment();
		if (ResponseUtils.isSucceed(data)) {
			// Parse FCI Template
			data = parseFCIProprietaryTemplate(data);
			// Extract application label
			if (ResponseUtils.isSucceed(data)) {
				// Get Aids
				List<byte[]> aids = getAids(data);
				for (byte[] aid : aids) {
					ret = extractPublicData(aid, extractApplicationLabel(data));
					if (ret == true) {
						break;
					}
				}
				if (!ret) {
					card.setNfcLocked(true);
				}
			}
		} else if(ResponseUtils.isEquals(data, SwEnum.SW_61)) {
			String resultHex = toHexString(data);

			data = getResponse(toByteArray("00C00000"+getStatus(resultHex, "sw2")));
			// Extract application label
			if (ResponseUtils.isSucceed(data)) {
				// Parse FCI Template
				data = parseFCIProprietaryTemplate(data);
				// Extract application label
				if (ResponseUtils.isSucceed(data)) {
					// Get Aids
					List<byte[]> aids = getAids(data);
					for (byte[] aid : aids) {
						ret = extractPublicData(aid, extractApplicationLabel(data));
						if (ret == true) {
							break;
						}
					}
					if (!ret) {
						card.setNfcLocked(true);
					}
				}
			}

		} else if (LOGGER.isDebugEnabled()) {
			LOGGER.debug((contactLess ? "PPSE" : "PSE") + " not found -> Use kown AID");
		}

		return ret;
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

	private String[] toHexArray(byte[] buffer) {

			String[] myArray = new String[buffer.length];
			if (buffer != null) {

			for (int i = 0; i < buffer.length; i++) {

				String hexChar = Integer.toHexString(buffer[i] & 0xFF);
				if (hexChar.length() == 1) {
					hexChar = "0" + hexChar;
				}

				 myArray[i] = hexChar.toUpperCase(Locale.US);
			}
		}

		return myArray;
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

	/**
	 * Method used to get the aid list, if the Kernel Identifier is defined, <br/>
	 * this value need to be appended to the ADF Name in the data field of <br/>
	 * the SELECT command.
	 *
	 * @param pData
	 *            FCI proprietary template data
	 * @return the Aid to select
	 */
	protected List<byte[]> getAids(final byte[] pData) {
		List<byte[]> ret = new ArrayList<byte[]>();
		List<TLV> listTlv = TlvUtil.getlistTLV(pData, EmvTags.AID_CARD, EmvTags.KERNEL_IDENTIFIER);
		for (TLV tlv : listTlv) {
			if (tlv.getTag() == EmvTags.KERNEL_IDENTIFIER && ret.size() != 0) {
				ret.add(ArrayUtils.addAll(ret.get(ret.size() - 1), tlv.getValueBytes()));
			} else {
				ret.add(tlv.getValueBytes());
			}
		}
		return ret;
	}

	/**
	 * Read EMV card with AID
	 */
	protected void readWithAID() throws CommunicationException {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Try to read card with AID");
		}
		// Test each card from know EMV AID
		for (EmvCardScheme type : EmvCardScheme.values()) {
			for (byte[] aid : type.getAidByte()) {
				if (extractPublicData(aid, type.getName())) {
					return;
				}
			}
		}
	}

	/**
	 * Select application with AID or RID
	 *
	 * @param pAid
	 *            byte array containing AID or RID
	 * @return response byte array
	 * @throws CommunicationException
	 */
	protected byte[] selectAID(final byte[] pAid) throws CommunicationException {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Select AID: " + BytesUtils.bytesToString(pAid));
		}
		return provider.transceive(new CommandApdu(CommandEnum.SELECT, pAid, 0).toBytes());
	}

	/**
	 * Read public card data from parameter AID
	 *
	 * @param pAid
	 *            card AID in bytes
	 * @param pApplicationLabel
	 *            application scheme (Application label)
	 * @return true if succeed false otherwise
	 */
	protected boolean extractPublicData(final byte[] pAid, final String pApplicationLabel) throws CommunicationException {
		boolean ret = false;
		// Select AID
		byte[] data = selectAID(pAid);
		// check response

		Log.i("errorcheck-1", toHexString(data));
		if (ResponseUtils.isSucceed(data) || ResponseUtils.isEquals(data, SwEnum.SW_61)) {

			if( ResponseUtils.isEquals(data, SwEnum.SW_61)){
				String resultHex = toHexString(data);
				data = getResponse(toByteArray("00C00000"+getStatus(resultHex, "sw2")));
				Log.i("errorcheck-2", toHexString(data));
			}
			// Parse select response
			ret = parse(data, provider);
			if (ret) {
				// Get AID
				String aid = BytesUtils.bytesToStringNoSpace(TlvUtil.getValue(data, EmvTags.DEDICATED_FILE_NAME));
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Application label:" + pApplicationLabel + " with Aid:" + aid);
				}
				card.setAid(aid);
				card.setType(findCardScheme(aid, card.getCardNumber()));
				card.setApplicationLabel(pApplicationLabel);
				card.setLeftPinTry(getLeftPinTry());
				Log.i("errorcheck-pinTry", Integer.toString(card.getLeftPinTry()));
			}
		}
		return ret;
	}

	/**
	 * Method used to find the real card scheme
	 *
	 * @param pAid
	 *            card complete AID
	 * @param pCardNumber
	 *            card number
	 * @return card scheme
	 */
	protected EmvCardScheme findCardScheme(final String pAid, final String pCardNumber) {
		EmvCardScheme type = EmvCardScheme.getCardTypeByAid(pAid);
		// Get real type for french card
		if (type == EmvCardScheme.CB) {
			type = EmvCardScheme.getCardTypeByCardNumber(pCardNumber);
			if (type != null) {
				LOGGER.debug("Real type:" + type.getName());
			}
		}
		return type;
	}

	/**
	 * Method used to extract Log Entry from Select response
	 *
	 * @param pSelectResponse
	 *            select response
	 * @return byte array
	 */
	protected byte[] getLogEntry(final byte[] pSelectResponse) {
		return TlvUtil.getValue(pSelectResponse, EmvTags.LOG_ENTRY, EmvTags.VISA_LOG_ENTRY);
	}

	/**
	 * Method used to parse EMV card
	 */
	protected boolean parse(final byte[] pSelectResponse, final IProvider pProvider) throws CommunicationException {
		boolean ret = false;
		// Get TLV log entry
		byte[] logEntry = getLogEntry(pSelectResponse);
		// Get PDOL
		byte[] pdol = TlvUtil.getValue(pSelectResponse, EmvTags.PDOL);
		// Send GPO Command
		byte[] gpo = getGetProcessingOptions(pdol, pProvider);
		Log.i("errorcheck-gpo-is :", toHexString(gpo));

		if( ResponseUtils.isEquals(gpo, SwEnum.SW_61)){
			String resultHex = toHexString(gpo);
			gpo = getResponse(toByteArray("00C00000"+getStatus(resultHex, "sw2")));
			Log.i("errorcheck-aa", toHexString(gpo));
		}

		// Check empty PDOL
		if (!ResponseUtils.isSucceed(gpo)) {
			gpo = getGetProcessingOptions(null, pProvider);
			// Check response
			if (!ResponseUtils.isSucceed(gpo)) {
				return false;
			}
		}

		// Extract commons card data (number, expire date, ...)
		if (extractCommonsCardData(gpo)) {

			// Extract log entry
			card.setListTransactions(extractLogEntry(logEntry));
			ret = true;
		}

		return ret;
	}

	/**
	 * Method used to extract commons card data
	 *
	 * @param pGpo
	 *            global processing options response
	 */
	protected boolean extractCommonsCardData(final byte[] pGpo) throws CommunicationException {
		boolean ret = false;
		// Extract data from Message Template 1
		byte data[] = TlvUtil.getValue(pGpo, EmvTags.RESPONSE_MESSAGE_TEMPLATE_1);
		if (data != null) {
			data = ArrayUtils.subarray(data, 2, data.length);
		} else { // Extract AFL data from Message template 2
			ret = TrackUtils.extractTrack2Data(card, pGpo);
			if (!ret) {
				data = TlvUtil.getValue(pGpo, EmvTags.APPLICATION_FILE_LOCATOR);
			} else {
				extractCardHolderName(pGpo);
			}
		}

		if (data != null) {
			// Extract Afl
			List<Afl> listAfl = extractAfl(data);
			// for each AFL
			for (Afl afl : listAfl) {
				// check all records
				for (int index = afl.getFirstRecord(); index <= afl.getLastRecord(); index++) {
					byte[] info = provider.transceive(new CommandApdu(CommandEnum.READ_RECORD, index, afl.getSfi() << 3 | 4, 0).toBytes());
					if (ResponseUtils.isEquals(info, SwEnum.SW_6C)) {
						info = provider.transceive(new CommandApdu(CommandEnum.READ_RECORD, index, afl.getSfi() << 3 | 4,
								info[info.length - 1]).toBytes());
					}

					// Extract card data
					if (ResponseUtils.isSucceed(info)) {
						extractCardHolderName(info);
						extractCVMList(info);
						if (TrackUtils.extractTrack2Data(card, info)) {
							return true;
						}
					}
				}
			}
		}
		return ret;
	}

	/**
	 * Method used to get log format
	 *
	 * @return list of tag and length for the log format
	 * @throws CommunicationException
	 */
	protected List<TagAndLength> getLogFormat() throws CommunicationException {
		List<TagAndLength> ret = new ArrayList<TagAndLength>();
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("GET log format");
		}
		// Get log format
		byte[] data = provider.transceive(new CommandApdu(CommandEnum.GET_DATA, 0x9F, 0x4F, 0).toBytes());
		if (ResponseUtils.isSucceed(data)) {
			ret = TlvUtil.parseTagAndLength(TlvUtil.getValue(data, EmvTags.LOG_FORMAT));
		}
		return ret;
	}

	/**
	 * Method used to extract log entry from card
	 *
	 * @param pLogEntry
	 *            log entry position
	 */
	protected List<EmvTransactionRecord> extractLogEntry(final byte[] pLogEntry) throws CommunicationException {
		List<EmvTransactionRecord> listRecord = new ArrayList<EmvTransactionRecord>();
		// If log entry is defined
		if (pLogEntry != null) {
			List<TagAndLength> tals = getLogFormat();
			// read all records
			for (int rec = 1; rec <= pLogEntry[1]; rec++) {
				byte[] response = provider.transceive(new CommandApdu(CommandEnum.READ_RECORD, rec, pLogEntry[0] << 3 | 4, 0).toBytes());
				// Extract data
				if (ResponseUtils.isSucceed(response)) {
					EmvTransactionRecord record = new EmvTransactionRecord();
					record.parse(response, tals);

					// Fix artifact in EMV VISA card
					if (record.getAmount() >= 1500000000) {
						record.setAmount(record.getAmount() - 1500000000);
					}

					// Skip transaction with nul amount
					if (record.getAmount() == null || record.getAmount() == 0) {
						continue;
					}

					if (record != null) {
						// Unknown currency
						if (record.getCurrency() == null) {
							record.setCurrency(CurrencyEnum.XXX);
						}
						listRecord.add(record);
					}
				} else {
					// No more transaction log or transaction disabled
					break;
				}
			}
		}
		return listRecord;
	}

	/**
	 * Extract list of application file locator from Afl response
	 *
	 * @param pAfl
	 *            AFL data
	 * @return list of AFL
	 */
	protected List<Afl> extractAfl(final byte[] pAfl) {
		List<Afl> list = new ArrayList<Afl>();
		ByteArrayInputStream bai = new ByteArrayInputStream(pAfl);
		while (bai.available() >= 4) {
			Afl afl = new Afl();
			afl.setSfi(bai.read() >> 3);
			afl.setFirstRecord(bai.read());
			afl.setLastRecord(bai.read());
			afl.setOfflineAuthentication(bai.read() == 1);
			list.add(afl);
		}
		return list;
	}

	/**
	 * Extract card holder lastname and firstname
	 *
	 * @param pData
	 *            card data
	 */
	protected void extractCardHolderName(final byte[] pData) {
		// Extract Card Holder name (if exist)
		byte[] cardHolderByte = TlvUtil.getValue(pData, EmvTags.CARDHOLDER_NAME);
		if (cardHolderByte != null) {
			String[] name = StringUtils.split(new String(cardHolderByte).trim(), CARD_HOLDER_NAME_SEPARATOR);
			if (name != null && name.length == 2) {
				card.setHolderFirstname(StringUtils.trimToNull(name[0]));
				card.setHolderLastname(StringUtils.trimToNull(name[1]));
			}
		}

		//set currency in the card
		byte[] cardHolderCurrency = TlvUtil.getValue(pData, EmvTags.APPLICATION_CURRENCY_CODE);
		if (cardHolderCurrency != null) {
			//String currency = toHexString(cardHolderCurrency);
			String currency = BytesUtils.bytesToStringNoSpace(cardHolderCurrency);
			//remove 0's in front
			currency = currency.replaceFirst("^0+(?!$)", "");
			card.setCurrency(currency);
		}
	}

	protected void extractCVMList(final byte[] pData) throws CommunicationException {
		// Extract Card Holder name (if exist)
		byte[] CVMList = TlvUtil.getValue(pData, EmvTags.CVM_LIST);
		if (CVMList != null) {

			//byte[] data = provider.transceive(toByteArray("0020008008248433FFFFFFFFFF"));
			//Log.i("errorcheck-verify", toHexString(data));

			//String[] name = StringUtils.split(new String(cardHolderByte).trim(), CARD_HOLDER_NAME_SEPARATOR);
			Log.i("errorcheck-cvmlist", toHexString(pData));
			Log.i("errorcheck-cvmlist", toHexString(CVMList));
			//Log.i("errorcheck-cvmlist", toHexString(cardHolderByte));
			cvm.setCVMRule(CVMList, card);
		}
	}

	/**
	 * Method used to create GPO command and execute it
	 *
	 * @param pPdol
	 *            PDOL data
	 * @param pProvider
	 *            provider
	 * @return return data
	 */
	protected byte[] getGetProcessingOptions(final byte[] pPdol, final IProvider pProvider) throws CommunicationException {
		// List Tag and length from PDOL
		List<TagAndLength> list = TlvUtil.parseTagAndLength(pPdol);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			out.write(EmvTags.COMMAND_TEMPLATE.getTagBytes()); // COMMAND
			// TEMPLATE
			out.write(TlvUtil.getLength(list)); // ADD total length
			if (list != null) {
				for (TagAndLength tl : list) {
					out.write(EmvTerminal.constructValue(tl));
				}
			}
		} catch (IOException ioe) {
			LOGGER.error("Construct GPO Command:" + ioe.getMessage(), ioe);
		}
		return pProvider.transceive(new CommandApdu(CommandEnum.GPO, out.toByteArray(), 0).toBytes());
	}

	/**
	 * Method used to get the field card
	 *
	 * @return the card
	 */
	public EmvCard getCard() {
		return card;
	}

}
