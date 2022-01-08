package messengerserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import com.google.gson.Gson;

public class ServerThread implements Runnable 
{


	
	int debugMask = 4; // Indicates the bit mask for Debugger usage. +1 the debugMask to indicate an error message.
	
	private Socket socket;

	private PrintWriter writer;
	private BufferedReader reader;

	private static final int PACKET_SIZE = 1024; // Determines the maximum size of transmitted packets.

	// Keeps track of information for sending heartbeans (keep-alive signals), in milliseconds.
	private static final long MAX_TIMEOUT = 5000;
	private static final long HEARTBEAT_WAIT = 2000; // How long after the last read we should wait before sending a heartbeat.
	private static final long HEARTBEAT_INTERVAL = 2000; // How long we should wait between heartbeats.
	private long lastHeartbeat = 0; // Stores when the last heartbeat was sent.
	private long lastReadTime = 0;

	// Keeps track of data unique to this session and user.

	private RegisteredUser user;

	private String username = "NONE";
	private int userID = -1;
	private String sessionID = "NONE";

	// Stores the most recent transmission
	private String lastTransmission = "";

	// Determines how long the hash salts are.
	private static final int SALT_LENGTH = 128;
	
	public ServerThread(Socket newSocket) 
	{
		socket = newSocket;
	}


	public void run() 
	{

        Debugger.record("Starting server thread " + getThreadID(), debugMask);
		listen();

	}

    public long getThreadID()
    {
        return Thread.currentThread().getId();
    }

	private boolean isAlive() {
		return (System.currentTimeMillis() - lastReadTime) < MAX_TIMEOUT;
	}

	private void sendHeartbeat()
	{
		transmit("HB");
	}
	
	private void listen() 
	{
		String input = "";
		
		try 
		{
			writer = new PrintWriter(socket.getOutputStream(), true);
			reader = new BufferedReader((new InputStreamReader(socket.getInputStream())));

			lastReadTime = System.currentTimeMillis();

			Debugger.record("Connection with server thread " + getThreadID() + " established.", 4);

			while(true) {

				if ((System.currentTimeMillis() - lastReadTime) > MAX_TIMEOUT)
				{
					onExit();
					return;
				}
				else if ((System.currentTimeMillis() - lastReadTime) > HEARTBEAT_WAIT)
				{
					if ((System.currentTimeMillis() - lastHeartbeat) > HEARTBEAT_INTERVAL)
						sendHeartbeat();
						lastHeartbeat = System.currentTimeMillis();
				}

				while (reader.ready())
				{
					lastReadTime = System.currentTimeMillis();

					input = reader.readLine();
					Debugger.record("Thread " + getThreadID() + " received: " + input, 4);

					handleMessage(input);
				}

//				try
//				{
//					writer.write(0);
//				}
//				catch (Exception e)
//				{
//					connected = false;
//					onExit();
//					return;
//				}

			}
		}
		catch(Exception e) 
		{
			Debugger.record("Thread " + getThreadID() + " has encountered an error: " + e.getMessage(), 5);
			e.printStackTrace();
		}

		Debugger.record("Thread " + getThreadID() + " is exiting.", debugMask);
		
	}
	
	public void transmit(String message)
	{

		Debugger.record("Transmitting with message: " + message, debugMask);


		if (message.length() > PACKET_SIZE)
		{
			int interval = PACKET_SIZE - 74; // 74 is the size of the Opcode + userID + SessionID + chatID.
			String header = message.substring(0, 74);
			String transmitMessage = message.substring(74);

			Debugger.record("Transmitting multi-message of size " + message.length() + " with header " + header, debugMask);


			// Transmits all parts of the message with a message that follows them.
			do
			{
				Debugger.record("Transmitting multi-message component " + transmitMessage.substring(0, interval), debugMask);
				writer.print("T" + header + transmitMessage.substring(0, interval));
				writer.flush();

				transmitMessage = transmitMessage.substring(interval);
				try
				{
					Thread.sleep(100);
				}
				catch (Exception e)
				{
					Debugger.record("ServerThread failed to sleep during transmit.", debugMask);
				}
			} while (transmitMessage.length() > interval);

			// Transmits the last segment of the message, with partial indicator set to F.

			Debugger.record("Transmitting final message component " + transmitMessage, debugMask);
			writer.print("F" + header + transmitMessage);
			writer.flush();
		}
		else
		{
			String transmitMessage = "F" + message;
			writer.print(transmitMessage);
			writer.flush();
		}

		lastTransmission = message;
	}

	public String getLastTransmission()
	{
		return lastTransmission;
	}



	private void handleMessage(String message) {
		
		HashMap<String, String> inputMap = Parser.parse(message);
		
		if (inputMap.containsKey("Opcode") == true) {
			
			String opcode = inputMap.get("Opcode");

			if (verifyMessageIntegrity(inputMap) == false)
			{
				Debugger.record("Server thread could not verify the integrity of a message. Provided User ID: " + inputMap.get("UserID") + "\nProvided Session ID: " + inputMap.get("SessionID") + "\nStored User ID: " + this.userID + "\nStored Session ID: " + this.sessionID, debugMask + 1);
				return;
			}

			Debugger.record("Entering message handling switch with opcode " + opcode, debugMask);

			switch (opcode) {
			
				case "IR":
					register(inputMap);
					return;
				case "LR":
					transmit(login(inputMap));
					return;
				case "PF":
					pullFriends(inputMap);
					return;
				case "AF":
					addFriend(inputMap);
					return;
				case "PR":
					pullFriendRequests(inputMap);
					return;
				case "US":
					searchUserName(inputMap);
					return;
				case "PC":
					pullUserChatPairs(inputMap);
					return;

				case "PM":
					pullMessages(inputMap);
					return;
				case "SM":
					sendMessage(inputMap);
					return;
				case "HB":
					// Heartbeat - Do nothing/
					return;
				case "ER":
					Debugger.record("Error code returned from Parser. Unable to handle message.", debugMask + 1);
					return;
				default:
					Debugger.record("Unrecognized opcode returned from Parser. Unable to handle message.", debugMask + 1);
					return;
			}
		}
		else {
			Debugger.record("Server thread received no opcode from parser.", debugMask + 1);
		}
	}

	/**
	 * This method verifies the integrity of a received message depending on the opcode.
	 *
	 * @param input The hashmap containing the parsed message.
	 * @return A boolean indicating whether or not the message could be verified successfully.
	 */
	public boolean verifyMessageIntegrity(HashMap<String, String> input)
	{
		String opcode = input.get("Opcode");

		if (opcode.equals("HB") || opcode.equals("IR") || opcode.equals("LR"))
		{
			// No session or userID assigned, can't be verified normally right now, just return true.
			return true;
		}
		else
		{
			// Input maps with any other opcode should contain a sessionID and userID that can be verified.

			if (input.containsKey("UserID") && input.containsKey("SessionID"))
			{
				String userID = input.get("UserID");
				String sessionID = input.get("SessionID");

				return ((userID.equals(Integer.toString(this.userID))) && (sessionID .equals(this.sessionID)));
			}
			else
			{
				Debugger.record("Input to verify message did not contain expected keys for opcode: " + opcode, debugMask + 1);
			}
		}
		return false;

	}

	public boolean register(HashMap<String, String> input) {
		
		String username = input.get("UserName");
		String password = input.get("Password");
		
		
		DatabaseConnection connection = DatabasePool.getConnection();
		
		HashMap<String, String> userResults = connection.getUser(username);
		
		if (userResults.containsKey("UserName")) {
			// A user with this name was found in the database.
			transmit("RU" + "The name you are trying to register is already taken.");
			Debugger.record("A user attempted to register the name " + username + ", but was rejected because it is taken.", debugMask + 1);
			connection.close();
			return false;
		}
		
		String passwordSalt = Cryptographer.generateRandomString(SALT_LENGTH);

		
		boolean createStatus = connection.createUser(username, password, passwordSalt);
		
		connection.close();
		
		if (createStatus == true) {
			
			transmit("RS" + "You have successfully registered with the username " + username);
			Debugger.record("A user has registered with the name " + username, debugMask);
		}

		return createStatus;
	
	}
	
	public String login(HashMap<String, String> input) {

		String transmitMessage = "";
		Debugger.record("Login activated by message handler in thread " + getThreadID(), debugMask);
		
		if (input.containsKey("UserName") == false) {
			Debugger.record("UserName field not present in login input.", debugMask + 1);
			transmitMessage = "LU" + "An undefined error has occurred while logging in. Please contact the system administrator.";
			return transmitMessage;
		}
		
		String username = input.get("UserName");
		
		if (checkUsernameSyntax(username) == false) {
			transmitMessage = "LU" + "Username " + username + " was invalid.";
			return transmitMessage;
		}
		
		DatabaseConnection connection = DatabasePool.getConnection();
		
		HashMap<String, String> queryResults = connection.getUser(username);
		
		if (queryResults.containsKey("UserName") == false) {
			Debugger.record("User attempted to login, but the username query returned no results.", debugMask);
			transmitMessage = "LU" + "Username was not found.";
			connection.close();
			return transmitMessage;
		}
		else if ((queryResults.containsKey("PasswordHash") == false) || (queryResults.containsKey("PasswordSalt") == false)) {

			Debugger.record("A user has attempted to login, but the query returned partial results.", debugMask);
			transmitMessage = "LU" + "An undefined error has occurred while logging in. Please contact the system administrator.";
			connection.close();
			return transmitMessage;
		}

		String userID = queryResults.get("UserID");
		String passwordGiven = input.get("Password");
		String passwordHash = queryResults.get("PasswordHash");
		String passwordSalt = queryResults.get("PasswordSalt");

		// Check the accuracy of the provided password.
		if (Cryptographer.verifyPassword(passwordGiven, passwordHash, passwordSalt) == false) {
			transmitMessage = "LU" + "Password provided did not match.";
			connection.close();
			return transmitMessage;
		}

		// All checks passed. Time to create a session.
		// Right now that just means assigning a 32-length string session ID and using it to verify transmissions.

		if (createSession())
		{
			try
			{
				this.username = username;
				this.userID = Integer.parseInt(userID);
			}
			catch (Exception e)
			{
				transmitMessage = "LU" + "An undefined error has occurred while logging in. Please contact the system administrator.";

				connection.close();
				return transmitMessage;

			}
			user = new RegisteredUser(this.userID, this.sessionID, this.writer);
			ServerController.addLoggedInUser(user);

			Debugger.record("User " + username + " has logged in successfully.", debugMask);
			transmitMessage = "LS" + Parser.pack(userID, ServerController.USER_ID_LENGTH) + Parser.pack(username, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You have logged in with the username " + username;

			connection.close();
			return transmitMessage;
		}

		Debugger.record("Login attempt made it to end of method without returning.", debugMask + 1);
		connection.close();
		return "LU" + "An undefined error has occurred while logging in. Please contact the system administrator.";

	}

	/**
	 * Gets a random 256 bit string from the Cryptographer, checks it against the server session
	 * list, and if it's not present assigns it to this login session and adds it to the server session map.
	 *
	 * @return A boolean indicating that the session was created successfully.
	 */

	private boolean createSession()
	{
		String newSessionID = "";
		do
		{
			newSessionID = Cryptographer.generateRandomString(32);
		} while (ServerController.addSession(newSessionID) == false);

		sessionID = newSessionID;
		return true;
	}


	
	public boolean pullFriends(HashMap<String, String> input) {
		
		DatabaseConnection connection = DatabasePool.getConnection();

		int userID = 0;
		try
		{
			userID = Integer.parseInt(input.get("UserID"));
		}
		catch (Exception e) {
			Debugger.record("User ID could not be parsed, or was not in the input to pulLFriends", debugMask + 1);
			connection.close();
			return false;
		}

		ArrayList<HashMap<String, String>> friends = connection.pullFriends(userID);

		Gson json = new Gson();
		String friendsJson = json.toJson(friends);

		transmit("FP" + Parser.pack(input.get("UserID"), ServerController.USER_ID_LENGTH) + sessionID + friendsJson);

		connection.close();

		return false;
	}


	
	public boolean addFriend(HashMap<String, String> input) {
		
		DatabaseConnection connection = DatabasePool.getConnection();

		int userOneID = 0;
		int userTwoID = 0;
		try
		{
			String userOne = input.get("UserID");
			String userTwo = input.get("UserName");

			userOneID = Integer.parseInt(userOne);
			userTwoID = Integer.parseInt(userTwo);
		}
		catch (Exception e)
		{
			Debugger.record("addFriend function failed to get or parse provided userIDs: " + e.getMessage(), debugMask + 1);

			String message = "ER" + Parser.pack(Integer.toString(this.userID), ServerController.USER_ID_LENGTH) + sessionID + "Unable to Server thread could not verify the integrity of a messagesend or approve friend request.";
			transmit(message);
			connection.close();
			return false;
		}

		if (connection.checkFriendRequest(userTwoID, userOneID) == true) {

			if (connection.addFriend(userOneID, userTwoID) && (connection.addFriend(userTwoID, userOneID)))
			{
				HashMap<String, String> userMap= new HashMap<>();
				userMap.put("UserID", input.get("UserID"));
				connection.close();
				return (pullFriends(userMap));
			}
			else
			{
				connection.close();
				return false;
			}
		}
		else {

			boolean returnVal = false;
			try
			{
				returnVal = connection.addFriendRequest(this.userID, this.username, userTwoID);

				// Sends a friend request notification to the other user if they are logged in.
				HashMap<String, RegisteredUser> loggedInUsers = ServerController.getLoggedInUsers();

				if (loggedInUsers.containsKey(userTwoID))
				{
					// Building the notification.
					RegisteredUser friend = loggedInUsers.get(userTwoID);
					String friendID = Parser.pack(friend.getUserIDstr(), 32);
					String friendSessionID = friend.getSessionID();

					HashMap<String, String> trimmedSender = new HashMap<>();

					trimmedSender.put("UserID", String.valueOf(this.userID));
					trimmedSender.put("UserName", this.username);
					Gson json = new Gson();
					String senderJson = json.toJson(trimmedSender);

					friend.sendTransmission("FR" + friendID + friendSessionID  + senderJson);
				}
			}
			catch (Exception e)
			{
				Debugger.record("Unable to send friend request notification: " + e.getMessage(), debugMask);
			}
			connection.close();
			return returnVal;
		}
	}

	/**
	 * Requests all friend requests associated with the currently logged in user from the database connection,
	 * then transmits the result.
	 * @param input A dictionary parsed from the incoming transmission.
	 * @return A boolean indicating success.
	 */

	public boolean pullFriendRequests(HashMap<String, String> input)
	{
		DatabaseConnection connection = DatabasePool.getConnection();
		boolean returnVal = false;
		try
		{
			ArrayList<HashMap<String, String>> friendRequests = connection.pullFriendRequests(this.userID);

			Gson json = new Gson();
			String friendRequestsJson = json.toJson(friendRequests);
			transmit("FR" + Parser.pack(this.userID, 32) + this.sessionID + friendRequestsJson);
			returnVal = true;
		}
		catch (Exception e)
		{
			Debugger.record("ServerThread unable to pull friend requests: " + e.getMessage(), debugMask);
		}

		connection.close();
		return returnVal;


	}

	public boolean removeFriend(HashMap<String, String> input)
	{
		DatabaseConnection connection = DatabasePool.getConnection();

		int userOneID = 0;
		int userTwoID = 0;

		try
		{
			String userOne = input.get("UserID");
			String userTwo = input.get("UserName");

			userOneID = Integer.parseInt(userOne);
			userTwoID = Integer.parseInt(userTwo);
		}
		catch (Exception e)
		{
			Debugger.record("RemoveFriend function failed to parse provided userIDs: " + e.getMessage(), debugMask + 1);
			connection.close();
			return false;
		}

		boolean returnVal = (connection.removeFriend(userOneID, userTwoID) && (connection.removeFriend(userTwoID, userOneID)));
		connection.close();
		return returnVal;
	}

	/**
	 * Calls the searchUserName function in DatabaseConnection and transmits the results, if any.
	 * @param input The parsed transmission from the client.
	 * @return A boolean indicating whether or not the operation was successful.
	 */

	public boolean searchUserName(HashMap<String, String> input)
	{
		String searchString;

		try
		{
			searchString = input.get("UserName");
		}
		catch (Exception e)
		{
			Debugger.record("Unable to get search string from input to search username function.", debugMask + 1);
			return false;
		}

		try {
			DatabaseConnection connection = DatabasePool.getConnection();

			Debugger.record("Server thread requesting username search from database.", debugMask);

			ArrayList<HashMap<String, String>> searchResults = connection.searchUserName(searchString);

			if (searchResults.size() > 0)
			{
				Gson json = new Gson();

				ArrayList<HashMap<String, String>> trimmedResults = new ArrayList<>();

				for (int i = 0; i < searchResults.size(); i++)
				{
					HashMap<String, String> user = searchResults.get(i);
					HashMap<String, String> trimmedUser = new HashMap<>();
					user.put("UserID", user.get("UserID"));
					user.put("UserName", user.get("UserName"));
					trimmedResults.add(user);
				}

				String searchResultsJson = json.toJson(trimmedResults);

				String transmitMessage = "UR" + Parser.pack(userID, ServerController.USER_ID_LENGTH) + sessionID + searchResultsJson;

				transmit(transmitMessage);

			}

			connection.close();

			return true;

		}
		catch (Exception e)
		{
			Debugger.record("Search username failed for unknown reasons: " + e.getMessage(), debugMask + 1);
		}

		return false;
	}


	public boolean pullUserChatPairs(HashMap<String, String> input)
	{


		int userID = 0;
		try
		{
			userID = Integer.parseInt(input.get("UserID"));
		}
		catch (Exception e)
		{
			Debugger.record("PullUserChatPairs failed to parse userID from input.", debugMask + 1);

			return false;
		}

		DatabaseConnection connection = DatabasePool.getConnection();

		ArrayList<HashMap<String, String>> chatPairs = connection.pullUserChats(userID);

		Gson json = new Gson();
		String chatPairsJson = json.toJson(chatPairs);

		String transmitMessage = "CP" + Parser.pack(userID, ServerController.USER_ID_LENGTH) + sessionID + chatPairsJson;
		transmit(transmitMessage);
		connection.close();
		return true;
	}



	public boolean pullMessages(HashMap<String, String> input) {

		DatabaseConnection connection = DatabasePool.getConnection();

		int chatID;
		try
		{
			chatID = Integer.parseInt(input.get("ChatID"));
		}
		catch (Exception e)
		{
			Debugger.record("PullMessages failed to get chatID.", debugMask + 1);
			return false;
		}
		try
		{
			ArrayList<HashMap<String, String>> messages = connection.pullMessagesFromChat(chatID);

			if (messages.size() > 0) {
				Gson json = new Gson();
				String messagesJson = json.toJson(messages);

				String transmitMessage = "MP" + Parser.pack(userID, ServerController.USER_ID_LENGTH) + sessionID + Parser.pack(chatID, ServerController.CHAT_ID_LENGTH) + messagesJson;
				transmit(transmitMessage);
			}
			else
			{
				Debugger.record("Message push called but found no results to send.", debugMask);
			}
			connection.close();
			return true;
		}
		catch (Exception e)
		{
			Debugger.record("PullMessages failed to get messages.", debugMask + 1);
		}
		connection.close();
		return false;
	}

	/**
	 * Sends a single message the DatabaseConnection to be added to a chat,
	 * and then spins up a thread to notify all users subscribed to that chat.
	 *
	 * @param input A dictionary containing the input received from the parser.
	 * @return A boolean indicating if a message has been successfully added to the database.
	 */
	public boolean sendMessage(HashMap<String, String> input) {

		int userID;
		int chatID;
		String message;
		String username;

		try
		{
			userID = Integer.parseInt(input.get("UserID"));
			chatID = Integer.parseInt(input.get("ChatID"));
			message = input.get("Message");
			username = input.get("UserName");
		}
		catch (Exception e)
		{
			Debugger.record("sendMessage failed to get or parse message parameters from input: " + e.getMessage(), debugMask + 1);
			return false;
		}

		DatabaseConnection connection = DatabasePool.getConnection();

		if (connection.addMessage(chatID, userID, username, message) == true)
		{
			Debugger.record("Server thread added message successfully, spinning up notification thread.", debugMask);
			NotificationThread notifier = new NotificationThread(chatID);

			new Thread(notifier).start();

			connection.close();
			return true;
		}
		connection.close();
		return false;
	}

	/**
	 * Verifies that a username a user is registering with meets the registration requirements.
	 * @param username The username to check.
	 * @return True if the name passes all checks, false otherwise.
	 */
	
	public boolean checkUsernameSyntax(String username) {
		
		if (username.length() < ServerController.MIN_USERNAME_LENGTH) {
			return false;
		}
		if (username.length() > ServerController.MAX_USERNAME_LENGTH) {
			return false;
		}

		return true;
	}
	
	public boolean checkPasswordSyntax(String password) {
		
		if (password.length() < ServerController.MIN_PASSWORD_LENGTH) {
			return false;
		}
		if (password.length() > ServerController.MAX_PASSWORD_LENGTH) {
			return false;
		}
		
		return true;
	}

	/**
	 * Handles clean up before the server thread exits.
	 */
	private void onExit() {

		try {

			// Remove the user from the logged in user map so no attempt is made to send them notifications.
			if (user != null) {
				ServerController.removeLoggedInUser(user);
			}
			// Remove the session from the servercontroller set so the ID can be reused.
			if (sessionID != "NONE") {
				ServerController.removeSession(sessionID);
			}
			// Close the print writer for the socket.
			if (writer != null) {
				writer.close();
			}
			// Close the reader for the socket.
			if (reader != null) {
				reader.close();
			}
			// Finally, close the socket itself.
			if (socket != null) {
				socket.close();
			}
		}
		catch (Exception e)
		{
			Debugger.record("ServerThread encountered error upon closing: " + e.getMessage(), debugMask + 1);
		}

        Debugger.record("Exiting server thread " + getThreadID(), debugMask);
        return;
	}
}
