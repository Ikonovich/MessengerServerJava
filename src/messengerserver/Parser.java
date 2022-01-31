package messengerserver;
import messengerserver.support.Opcodes;

import java.util.HashMap;


// The first three characters of a transmission MUST BE as follows:
//
// [Index 0] - Multiple Message Indicator - If T (True), the ServerThread stores the message in a transmission buffer.
// When an F (False) transmission is received following a T transmission or multiple T transmissions, the
// buffer contents are sent to be parsed and the buffer is cleared.
// [Index 1-2] - Opcode - Determines how the remainder of the message is parsed by being assigned to a bitmask.
//
//
//As such, all information in a server message __must__ be in the bitmask appropriate order with the
//appropriate sizes, and a field __must__ not be present if not required by the given Opcode.
//
// Padding of input strings is done using asterisks (*) at this time, using the Parser.Pack function.
//
//The bitmask order is as follows:
//
// 00001: UserID - 32 characters - Present for all messages except login and registration.
// 00010: UserName - 32 characters - Present for login, registration, and when adding a friend.
// 00100: Password - 128 characters - Present only for login and registration.
// 01000: Session ID - 32 characters - Required for all non-login and non-registration interactions. Very weakly verifies connection
// integrity.
// 10000: Chat ID - 8 characters - Identifies a single chat between one or multiple people.
// 100000: Message ID - 32 characters - Identifies a single message.
//
// The final component of a received transmission, the Message, is whatever remains after the item determined by the bit mask are parsed out.
// See "MessageDefinitions.txt" for full information on transmission types and parsing.

public class Parser {
	
	private static final int debugMask = 8; // Indicates the bit mask for Debugger usage. +1 the debugMask to indicate an error message.

	
	// Used to determine the parser behavior.
	
	private static final int userNameLength = ServerController.MAX_USERNAME_LENGTH;
	private static final int userIDLength = ServerController.USER_ID_LENGTH;
	private static final int passwordLength = ServerController.MAX_PASSWORD_LENGTH;
	private static final int sessionIDLength = ServerController.SESSION_ID_LENGTH;
	private static final int chatIDLength = ServerController.CHAT_ID_LENGTH;
	
	
	
	private static final HashMap<String, Integer> opcodeMap;
	
	static {

		// Defines the parsing components/bitmask for each message type.
		opcodeMap = new HashMap<String, Integer>();
		
		opcodeMap.put("IR", Opcodes.USERNAME | Opcodes.PASSWORD);
		opcodeMap.put("LR", Opcodes.USERNAME | Opcodes.PASSWORD);
		opcodeMap.put("LO", Opcodes.USERID | Opcodes.SESSIONID);
		opcodeMap.put("PF", Opcodes.USERID | Opcodes.SESSIONID);
		opcodeMap.put("RF", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.USERNAME);
		opcodeMap.put("SR", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.USERNAME);
		opcodeMap.put("AR", Opcodes.USERID | Opcodes.SESSIONID);
		opcodeMap.put("DR", Opcodes.USERID | Opcodes.SESSIONID);
		opcodeMap.put("PR", Opcodes.USERID | Opcodes.SESSIONID);
		opcodeMap.put("PC", Opcodes.USERID | Opcodes.SESSIONID);
		opcodeMap.put("CC", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.USERNAME);
		opcodeMap.put("CI", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.USERNAME);
		opcodeMap.put("CO", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.CHATID);
		opcodeMap.put("US", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.USERNAME);
		opcodeMap.put("UC", Opcodes.USERID | Opcodes.SESSIONID);
		opcodeMap.put("PM", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.CHATID);
		opcodeMap.put("SM", Opcodes.USERID | Opcodes.USERNAME | Opcodes.SESSIONID | Opcodes.CHATID);
		opcodeMap.put("EM", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.MESSAGEID);
		opcodeMap.put("DM", Opcodes.USERID | Opcodes.SESSIONID | Opcodes.MESSAGEID);
		opcodeMap.put("HB", 0);
	}
	
	
	public static HashMap<String, String> parse(String input) 
	{

		Debugger.record("Parsing: " + input, debugMask);
		
		HashMap<String, String> returnMap = new HashMap<String, String>();		
		// Gets the opcode.
		String opcode = "ER";
		String partialIndicator = "";
		String message = input;
		
		try {
			partialIndicator = input.substring(0, 1);
			opcode = input.substring(1, 3);
			message = input.substring(3);
			returnMap.put("Opcode", opcode);
		}
		catch(Exception e) {
			
			returnMap.put("Opcode", "ER");
			Debugger.record("Parser failed getting opcode", debugMask + 1);
			return returnMap;
		}
		
		
		// Getting the bitmask from the opcode.
		int mask = 0;
		if (opcodeMap.containsKey(opcode)) {
			mask = opcodeMap.get(opcode);
		}
		else {
			
			returnMap.put("Opcode", opcode);
			Debugger.record("Parser failed at bit 0 for opcode: " + opcode + " with input: " + message, debugMask + 1);
			return returnMap;
		}

		// Begin the fall through parser here.
		
		if ((mask & 1) > 0)
		{
			
			try {
				returnMap.put("UserID", Parser.unpack(message.substring(0, ServerController.USER_ID_LENGTH)));

				Debugger.record("Parser processing at bit 1 for opcode: " + opcode + " with input: " + message, debugMask);

				message = message.substring(ServerController.USER_ID_LENGTH);
				

			}
			catch (Exception e) 
			{
				Debugger.record("Parser failed at bit 1 for opcode: " + opcode + " with input: " + message, debugMask + 1);
				returnMap.put("Opcode", "ER");
				return returnMap;
			}
			
		}
		if ((mask & 2) > 0)
		{

			try {
				Debugger.record("Parser processed at bit 2 for opcode: " + opcode + " with input: " + message, debugMask);
				returnMap.put("UserName", Parser.unpack(message.substring(0, ServerController.MAX_USERNAME_LENGTH)));
				message = message.substring(ServerController.MAX_USERNAME_LENGTH);
				
			}
			catch (Exception e) 
			{
				Debugger.record("Parser failed at bit 2 for opcode: " + opcode + " with input: " + message, debugMask + 1);
				returnMap.put("Opcode", "ER");
				return returnMap;
			}
			
			
		}
		if ((mask & 4) > 0)
		{

			try {
				Debugger.record("Parser processed at bit 3 for opcode: " + opcode + " with input: " + message, debugMask);

				returnMap.put("Password", Parser.unpack(message.substring(0, 128)));
				message = message.substring(ServerController.MAX_PASSWORD_LENGTH);

			}
			catch (Exception e) 
			{
				Debugger.record("Parser failed at bit 3 for opcode: " + opcode + " with input: " + message, debugMask + 1);
				returnMap.put("Opcode", "ER");
				return returnMap;
			}
			
		}
		if ((mask & 8) > 0)
		{
			try {

				returnMap.put("SessionID", message.substring(0, ServerController.SESSION_ID_LENGTH));
				Debugger.record("Parser processing at bit 4 for opcode: " + opcode + " with input: " + message, debugMask);
				message = message.substring(ServerController.SESSION_ID_LENGTH);
			}
			catch (Exception e) 
			{
				Debugger.record("Parser failed at bit 4 for opcode: " + opcode + " with input: " + message, debugMask + 1);
				returnMap.put("Opcode", "ER");
				return returnMap;
			}
			
		}
		if ((mask & 16) > 0)
		{

			try {
				returnMap.put("ChatID", message.substring(0, ServerController.CHAT_ID_LENGTH));
				Debugger.record("Parser processing at bit 5 for opcode: " + opcode + " with input: " + message, debugMask);


				message = message.substring(ServerController.CHAT_ID_LENGTH);
				

			}
			catch (Exception e) 
			{
				Debugger.record("Parser failed at bit 5 for opcode: " + opcode + " with input: " + message + " Exception: " + e.getMessage(), debugMask + 1);
				returnMap.put("Opcode", "ER");
				return returnMap;
			}
			
		}

		if ((mask & 32) > 0)
		{

			try {
				returnMap.put("MessageID", Parser.unpack(message.substring(0, ServerController.MESSAGE_ID_LENGTH)));

				Debugger.record("Parser processing at bit 6 for opcode: " + opcode + " with input: " + message, debugMask);

				message = message.substring(ServerController.MESSAGE_ID_LENGTH);


			}
			catch (Exception e)
			{
				Debugger.record("Parser failed at bit 6 for opcode: " + opcode + " with input: " + message + " Exception: " + e.getMessage(), debugMask + 1);
				returnMap.put("Opcode", "ER");
				return returnMap;
			}

		}
		
		if (message.length() > 0) 
		{
			returnMap.put("Message", message);
		}
		
		return returnMap;
	}
	
	// Used to pack and unpack variable-length components of transmissions such as username or password.
	
	public static String pack(String input, int size) {
		
		String newString = input;
		
        if (newString.length() > size)
        {
        	newString = newString.substring(0, size);
        }

		StringBuilder builder = new StringBuilder(newString);
        for (int i = newString.length(); i < size; i++)
        {
        	builder.append("*");
        }
        
        Debugger.record("Packed " + input + " into " + newString + "\n", debugMask);

        return builder.toString();
	}

	public static String pack(int input, int size)
	{
		String inputString = String.valueOf(input);
		return pack(inputString, size);
	}
	public static String unpack(String input) {
		
        int packStart = input.indexOf("*");

        String unpackedString = input.substring(0, packStart);

        Debugger.record("Unpacked " + input + " into " + unpackedString + "\n", debugMask);
        
        return unpackedString;
		
	}
}