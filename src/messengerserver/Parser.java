package messengerserver;
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
//
// The final component of a received transmission, the Message, is whatever remains after the item determined by the bit mask are parsed out.
//
// The core server opcodes with their bitmasks are:
//
// IR (Initial Registration):  00110  /  6
// LR (Login Request):  00110  /   6
// LO (Logout Request):  01001  /   9
// PF (Pull Friends):  01001  /   9
// AF (Add Friend):  01011  / 11
// PR (Pull Friend Requests): 01001 / 9
// US (User Search): 01011 / 11
// PC (Pull User-Chat Pairs) / 01001 / 9
// PM (Pull Messages From Chat):  11001    / 25
// CC (Create Chat): 01011 / 11  -- For this code, the "Message" component stores the desired chat name
// SM (Send Message):  11001   /   27
// HB (Heartbeat): 00000 / 0
//
// The core client opcodes with their bitmasks are:
//
// RU (Registration unsuccessful):  00010 / 2
// RS (Registration successful):  00010  / 2
// LU (Login unsuccessful):	 00010 / 2
// LS (Login successful):  01011 / 11
// FP (Friend Push): 01001 / 9
// UR (User search Results) : 01001 / 9
// CP (User-Chat Pairs Push): 01001 / 9
// MP (Message Push for one chat): 11001 / 25
// CN  (Chat Notification): 11001 / 25
// AM (Administrative Message): 01001 / 9
// HB (Heartbeat): 00000 / 0



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
		
		opcodeMap = new HashMap<String, Integer>();
		
		opcodeMap.put("IR", 6);
		opcodeMap.put("LR", 6);
		opcodeMap.put("LO", 9);
		opcodeMap.put("PF", 9);
		opcodeMap.put("AF", 11);
		opcodeMap.put("PR", 9);
		opcodeMap.put("PC", 9);
		opcodeMap.put("CC", 11);
		opcodeMap.put("US", 11);
		opcodeMap.put("UC", 9);
		opcodeMap.put("PM", 25);
		opcodeMap.put("SM", 27);
		opcodeMap.put("HB", 0);
	}
	
	
	public static HashMap<String, String> parse(String input) 
	{
		
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
				returnMap.put("UserID", Parser.unpack(message.substring(0, userIDLength)));
				message = message.substring(userIDLength);
				
				Debugger.record("Parser processed at bit 1 for opcode: " + opcode + " with input: " + message, debugMask);

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

				returnMap.put("UserName", Parser.unpack(message.substring(0, userNameLength)));
				message = message.substring(userNameLength);
				
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
				message = message.substring(passwordLength);

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
				Debugger.record("Parser processed at bit 4 for opcode: " + opcode + " with input: " + message, debugMask);

				returnMap.put("SessionID", message.substring(0, sessionIDLength));
				message = message.substring(sessionIDLength);
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
				returnMap.put("ChatID", message.substring(0, chatIDLength));
				message = message.substring(chatIDLength);
				
				Debugger.record("Parser processed at bit 5 for opcode: " + opcode + " with input: " + message, debugMask);

			}
			catch (Exception e) 
			{
				Debugger.record("Parser failed at bit 5 for opcode: " + opcode + " with input: " + message + " Exception: " + e.getMessage(), debugMask + 1);
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