package messengerserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import javax.xml.crypto.Data;

public class ServerThread implements Runnable 
{

	private static final int debugMask = 4; // Indicates the bit mask for Debugger usage. +1 the debugMask to indicate an error message.
	
	private final Socket socket;

	private PrintWriter writer;
	private BufferedReader reader;


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


	private void sendHeartbeat()
	{
		transmit("HB");
	}
	
	private void listen() 
	{
		String input;
		
		try 
		{
			writer = new PrintWriter(socket.getOutputStream(), true);
			reader = new BufferedReader((new InputStreamReader(socket.getInputStream())));

			lastReadTime = System.currentTimeMillis();

			Debugger.record("Connection with server thread " + getThreadID() + " established.", 4);

			while(true) {

				if ((System.currentTimeMillis() - lastReadTime) > ServerController.MAX_TIMEOUT)
				{
					onExit();
					Debugger.record("Thread " + getThreadID() + " is exiting.", debugMask);
					return;
				}
				else if ((System.currentTimeMillis() - lastReadTime) > ServerController.HEARTBEAT_WAIT)
				{
					if ((System.currentTimeMillis() - lastHeartbeat) > ServerController.HEARTBEAT_INTERVAL)
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
			}
		}
		catch(Exception e) 
		{
			Debugger.record("Thread " + getThreadID() + " has encountered an error: " + e.getMessage(), 5);
			e.printStackTrace();
		}

		Debugger.record("Thread " + getThreadID() + " is exiting.", debugMask);
		
	}


	private void transmit(String message)
	{

		Debugger.record("Transmitting with message: " + message, debugMask);


		if (message.length() > ServerController.PACKET_SIZE)
		{
			int interval = ServerController.PACKET_SIZE - 74; // 74 is the size of the Opcode + userID + SessionID + chatID.
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
					Thread.sleep(3);
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

	/**
	 * This is a public, static version of the transmit function that allows other objects to transmit if they
	 * have a printwriter to transmit on.
	 * @param message The message to be transmitted.
	 * @param writer The writer to be written to.
	 */

	public static void transmit(String message, PrintWriter writer)
	{

		Debugger.record("Transmitting with message: " + message, debugMask);


		if (message.length() > ServerController.PACKET_SIZE)
		{
			int interval = ServerController.PACKET_SIZE - 74; // 74 is the size of the Opcode + userID + SessionID + chatID.
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

	}


	/**
	 * This message takes a message received by the socket, sends it to be parsed,
	 * and then uses the result of the parse to direct it to the appropriate handling method.
	 * @param message The message received on the socket.
	 */

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
				case "LO":
					logout(inputMap);
					return;
				case "PF":
					pullFriends(inputMap);
					return;
				case "AF":
					addFriend(inputMap);
					return;
				case "RF":
					removeFriend(inputMap);
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
				case "CC":
					createChat(inputMap);
					return;
				case "MP":
					modifyPermissions(inputMap);
				case "PM":
					pullMessages(inputMap);
					return;
				case "SM":
					sendMessage(inputMap);
					return;
				case "EM":
					editMessage(inputMap);
					return;
				case "DM":
					deleteMessage(inputMap);
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

		try {
			String username = input.get("UserName");
			String password = input.get("Password");

			if (checkUsernameSyntax(username) == false)
			{
				transmit("RU" + Parser.pack(username, ServerController.MAX_USERNAME_LENGTH) + "The provided " +
						 "username must be between 8 and 32 characters in length, and may contain only " +
						"alphanumeric characters and dashes.");

				return false;
			}

			if (checkPasswordSyntax(password) == false)
			{
				transmit("RU" + Parser.pack(username, ServerController.MAX_USERNAME_LENGTH) + "The provided " +
						"password must be between 8 and 128 characters in length.");
				return false;
			}
			DatabaseConnection connection = DatabasePool.getConnection();

			HashMap<String, String> userResults = connection.getUser(username);

			if (userResults.containsKey("UserName")) {
				// A user with this name was found in the database.
				transmit("RU" + Parser.pack(username, ServerController.MAX_USERNAME_LENGTH) + "The name you are " +
						"trying to register is already taken.");

				Debugger.record("A user attempted to register the name " + username + ", but was " +
						"rejected because it is taken.", debugMask + 1);
				connection.close();
				return false;
			}

			String passwordSalt = Cryptographer.generateRandomString(SALT_LENGTH);
			String passwordHash = Cryptographer.hashPassword(password, passwordSalt);

			boolean createStatus = connection.createUser(username, passwordHash, passwordSalt);

			connection.close();

			if (createStatus == true) {

				transmit("RS" + Parser.pack(username, ServerController.MAX_USERNAME_LENGTH) + "You have " +
						"successfully registered with the username " + username);

				Debugger.record("A user has registered with the name " + username, debugMask);
				return true;
			}
		}
		catch (Exception e)
		{
			Debugger.record("DB Connection failed to create user.", debugMask);
		}

		return false;
	}
	
	public String login(HashMap<String, String> input) {

		String transmitMessage = "";
		Debugger.record("Login activated by message handler in thread " + getThreadID(), debugMask);
		
		if (input.containsKey("UserName") == false) {
			Debugger.record("UserName field not present in login input.", debugMask + 1);
			transmitMessage = "LU" + "An undefined error has occurred while logging in. Please contact the " +
					"system administrator.";
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
	 * Logs the user account out while leaving the client connected to the server.
	 * @param input The hashmap containing the parsed message. Should contain a SessionID and UserID..
	 * @return A boolean indicating if the logout was successful.
	 */
	public boolean logout(HashMap<String, String> input)
	{
		try {

			// Remove the user from the logged in user map so no attempt is made to send them notifications.
			if (this.user != null) {
				ServerController.removeLoggedInUser(this.user);
			}
			// Remove the session from the servercontroller set so the ID can be reused.
			if (this.sessionID != "NONE") {
				ServerController.removeSession(this.sessionID);

				transmit("LO" + Parser.pack(this.userID, ServerController.USER_ID_LENGTH) + this.sessionID + "You have logged out successfully.");

				this.userID = 0;
				this.username = "NONE";
				this.user = null;
				this.sessionID = "NONE";
			}

		}
		catch (NullPointerException e) {

			return false;
		}
		return true;

	}

	/**
	 * Gets a random 256 bit string from the Cryptographer, checks it against the server session
	 * list, and if it's not present assigns it to this login session and adds it to the server session map.
	 *
	 * @return A boolean indicating that the session was created successfully.
	 */

	private boolean createSession()
	{
		String newSessionID;
		do
		{
			newSessionID = Cryptographer.generateRandomString(ServerController.SESSION_ID_LENGTH);
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


	/**
	 * This function processes all add friend requests sent by this user's client.
	 * It takes two users, userOne and userTwo. UserOne should be the user that sent
	 * the friend request. It first checks to see if a corresponding friend request from
	 * userTwo to UserOne is in the database. If it is, it gets a DB connection and calls
	 * database.addFriend, which creates all the database items necessary to have two users as
	 * friends.
	 *
	 * If the corresponding pair does *not* exist, this function sends a friend request
	 * to the second user.
	 *
	 * @param input A dictionary containing UserID and UserName keys, corresponding to
	 *              userOne's ID and userTwo's ID.
	 * @return A boolean indicating success or failure.
	 */
	public boolean addFriend(HashMap<String, String> input) {
		
		DatabaseConnection connection = DatabasePool.getConnection();

		int userOneID = 0;
		int userTwoID = 0;
		String userOneIDstr;
		String userTwoIDstr;

		Debugger.record("addFriend function working", debugMask);

		try
		{
			userOneIDstr = input.get("UserID");
			userTwoIDstr = input.get("UserName");

			userOneID = Integer.parseInt(userOneIDstr);
			userTwoID = Integer.parseInt(userTwoIDstr);
		}
		catch (Exception e)
		{
			Debugger.record("addFriend function failed to get or parse provided userIDs: " + e.getMessage(), debugMask + 1);

			String message = "ER" + Parser.pack(Integer.toString(this.userID), ServerController.USER_ID_LENGTH) + sessionID + "Unable to Server thread could not verify the integrity of a messagesend or approve friend request.";
			transmit(message);
			connection.close();
			return false;
		}

		// Get the username of the friend.
		HashMap<String, String> friend = connection.getUser(userTwoID);

		String friendName;
		try {
			friendName = friend.get("UserName");
		}
		catch (NullPointerException ex)
		{
			Debugger.record("ServerThread addFriend: Server tried to add a nonexistent friend or something.", debugMask + 1);
			return false;
		}

		if (connection.checkFriendRequest(userTwoID, userOneID) == true) {
			if (connection.addFriend(userOneID, userTwoID)) {
				// Removes the old friend request.
				connection.removeFriendRequest(userTwoID, userOneID);

				HashMap<String, String> userMap = new HashMap<>();
				userMap.put("UserID", input.get("UserID"));

				HashMap<String, RegisteredUser> users = ServerController.getLoggedInUsers();


				if (users.containsKey(userTwoIDstr)) {
					RegisteredUser userTwo = users.get(userTwoIDstr);
					userTwo.sendTransmission("AM" + Parser.pack(this.userID, ServerController.MAX_USERNAME_LENGTH) + this.sessionID + this.username + " has accepted your friend request!");
					userTwo.pushFriends();
				}

				connection.close();

				transmit("AM" + Parser.pack(this.userID, ServerController.USER_ID_LENGTH) + this.sessionID + "You are now friends with " + friendName + "!");

				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					Debugger.record("Attempt to sleep was interrupted.", debugMask);
				}

				// Causes this user's visible friends to update.
				pullFriends(input);

				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					Debugger.record("Attempt to sleep was interrupted.", debugMask);
				}

				// Causes this user's visible requests to update to reflect the change.
				pullFriendRequests(input);
			} else {
				Debugger.record("addFriend call to database returned false", debugMask + 1);
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

				transmit("AM" + Parser.pack(this.userID, ServerController.USER_ID_LENGTH) + this.sessionID + "A friend request has been sent to " + friendName + "!");


				if (loggedInUsers.containsKey(userTwoIDstr))
				{
					// Building the notification.
					RegisteredUser friendUser = loggedInUsers.get(userTwoIDstr);
					String friendID = Parser.pack(friendUser.getUserIDstr(), 32);
					String friendSessionID = friendUser.getSessionID();

					HashMap<String, String> trimmedSender = new HashMap<>();

					trimmedSender.put("UserID", String.valueOf(this.userID));
					trimmedSender.put("UserName", this.username);

					ArrayList<HashMap<String, String>> senderList = new ArrayList<>();
					senderList.add(trimmedSender);

					Gson json = new Gson();
					String senderJson = json.toJson(senderList);

					friendUser.sendTransmission("FR" + friendID + friendSessionID  + senderJson);
				}
			}
			catch (Exception e)
			{
				Debugger.record("Unable to send friend request notification: " + e.getMessage(), debugMask);
			}
			connection.close();
			return returnVal;
		}
		return false;
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

	public boolean removeFriendRequest(HashMap<String, String> input)
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

		boolean returnVal = (connection.removeFriendRequest(userOneID, userTwoID));
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

		if (returnVal == true) {
			transmit("AM" + Parser.pack(this.userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "Friend removed successfully.");
			pullFriends(input);
		}
		return returnVal;
	}

	/**
	 * Calls the searchUserName function in DatabaseConnection and transmits the results, if any.
	 * @param input The parsed transmission from the client.
	 * @return A boolean indicating if the operation was successful.
	 */

	public boolean searchUserName(HashMap<String, String> input) {
		String searchString;

		try {
			searchString = input.get("UserName");
		} catch (Exception e) {
			Debugger.record("Unable to get search string from input to search username function.", debugMask + 1);
			return false;
		}
		if (searchString.length() > 2)
		{

			try {
				DatabaseConnection connection = DatabasePool.getConnection();

				Debugger.record("Server thread requesting username search from database.", debugMask);

				ArrayList<HashMap<String, String>> searchResults = connection.searchUserName(searchString);

				if (searchResults.size() > 0) {
					Gson json = new Gson();

					ArrayList<HashMap<String, String>> trimmedResults = new ArrayList<>();

					for (int i = 0; i < searchResults.size(); i++) {
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

			} catch (Exception e) {
				Debugger.record("Search username failed for unknown reasons: " + e.getMessage(), debugMask + 1);
			}
		}
		return false;
	}

	/**
	 * Creates a single new chat with the parameters provided in the input.
	 * @param input The parsed transmission from the client.
	 * @return A boolean indicating if the operation was successful.
	 */

	public boolean createChat(HashMap<String, String> input) {

		String chatName;
		int creatorID;
		String creatorName;
		try
		{
			creatorID = Integer.parseInt(input.get("UserID"));
			creatorName = input.get("UserName");
			chatName = input.get("Message");
		}
		catch (NullPointerException e)
		{
			Debugger.record("Message not found in input to create chat.", debugMask);
			return false;
		}

		DatabaseConnection connection = DatabasePool.getConnection();

		connection.createChat(chatName, creatorID, creatorName);
		return true;
	}


	 /**
	 * Takes a message component containing a dictionary containing "Command" and "UserID" components.
	 * Checks if the sending user has permissions to run the command. If so, the command is applied.
	 * @param input The parsed transmission from the client.
	 * @return A boolean indicating if the operation was successful.
	 */
	public boolean modifyPermissions(HashMap<String, String> input)
	{
		int userID;
		int commandedUserID;
		int chatID;
		HashMap<String, String> commandDict;
		String command;

		try
		{
			userID = Integer.parseInt(input.get("UserID"));
			chatID = Integer.parseInt(input.get("ChatID"));

			String commandJson = input.get("Message");

			Gson gson = new Gson();

			commandDict = gson.fromJson(commandJson, HashMap.class);

			commandedUserID = Integer.parseInt(commandDict.get("UserID"));
			command = commandDict.get("Command");
		}
		catch (NumberFormatException e) {
			Debugger.record("Provided ID for chatCommand was invalid: " + e.getMessage(), debugMask);
			return false;
		}
		catch (NullPointerException e)
		{
			Debugger.record("chatCommand input doesn't contain appropriate entries: " + e.getMessage(), debugMask);
			return false;
		}
		catch (JsonSyntaxException e)
		{
			Debugger.record("chatCommand input message can't be parsed to Json: " + e.getMessage(), debugMask);
			return false;
		}

		DatabaseConnection connection = DatabasePool.getConnection();

		HashMap<String, String> userPair = connection.pullSingleUserChatPair(chatID, userID);
		HashMap<String, String> commandedUserPair = connection.pullSingleUserChatPair(chatID, commandedUserID);

		int userPermissions;
		int commandedUserPermissions;

		try
		{
			userPermissions = Integer.parseInt(userPair.get("Permissions"));
			commandedUserPermissions = Integer.parseInt(userPair.get("Permissions"));
		}
		catch (NullPointerException e)
		{
			Debugger.record("User pair dictionaries did not contain permissions.", debugMask);
			return false;
		}
		catch (NumberFormatException e)
		{
			Debugger.record("User pair dictionaries contained invalid permissions: " + e.getMessage(), debugMask);
			return false;
		}

		if (commandedUserPermissions >= userPermissions)
		{
			transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
			return false;
		}

		int permissionAdjustment = 0;
		switch (command)
		{
			case "RestrictUploading":
				if ((userPermissions & Permissions.RESTRICT) == Permissions.RESTRICT)
				{
					permissionAdjustment = -Permissions.UPLOAD;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "AllowUploading":
				if ((userPermissions & Permissions.RESTRICT) == Permissions.RESTRICT)
				{
					permissionAdjustment = Permissions.UPLOAD;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "Mute":
				if ((userPermissions & Permissions.MUTE) == Permissions.MUTE)
				{
					permissionAdjustment = -Permissions.TALK;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "Unmute":
				if ((userPermissions & Permissions.MUTE) == Permissions.MUTE)
				{
					permissionAdjustment = Permissions.TALK;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "Ban":
				if ((userPermissions & Permissions.BAN) == Permissions.BAN)
				{
					permissionAdjustment = -(Permissions.READ & Permissions.TALK);
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "Unban":
				if ((userPermissions & Permissions.BAN) == Permissions.BAN)
				{
					permissionAdjustment = Permissions.READ & Permissions.TALK;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "AllowRestrict":
				if ((userPermissions & Permissions.OWNER) == Permissions.OWNER)
				{
					permissionAdjustment = Permissions.RESTRICT;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "DisallowRestrict":
				if ((userPermissions & Permissions.OWNER) == Permissions.OWNER)
				{
					permissionAdjustment = -Permissions.RESTRICT;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "AllowMute":
				if ((userPermissions & Permissions.OWNER) == Permissions.OWNER)
				{
					permissionAdjustment = Permissions.MUTE;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "DisallowMute":
				if ((userPermissions & Permissions.OWNER) == Permissions.OWNER)
				{
					permissionAdjustment = -Permissions.MUTE;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "AllowBan":
				if ((userPermissions & Permissions.OWNER) == Permissions.OWNER)
				{
					permissionAdjustment = Permissions.BAN;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
			case "DisallowBan":
				if ((userPermissions & Permissions.OWNER) == Permissions.OWNER)
				{
					permissionAdjustment = -Permissions.BAN;
				}
				else
				{
					transmit("AM" + Parser.pack(userID, ServerController.MAX_USERNAME_LENGTH) + sessionID + "You do not have the required permissions to " + command + " userID " + commandedUserID);
					return false;
				}
				break;
		}
		int permissions = commandedUserPermissions + permissionAdjustment;
		boolean returnVal = connection.updatePermissions(userID, commandedUserID, chatID, permissions);


		if (returnVal == true)
		{
			connection.addMessage(chatID, 0, "Controller", "Command " + command + " has been applied to user " + commandedUserID);
		}
		else
		{
			connection.addMessage(chatID, 0, "Controller", "Command " + command + " has failed.");
		}

		return returnVal;
	}

	/**
	 * Pulls all of the chat pairs for a single user from the database and transmits them.
	 * @param input The parsed transmission from the client.
	 * @return A boolean indicating if the operation was successful.
	 */

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
				String transmitMessage = "MP" + Parser.pack(userID, ServerController.USER_ID_LENGTH) + sessionID + Parser.pack(chatID, ServerController.CHAT_ID_LENGTH);
				transmit(transmitMessage);
				Debugger.record("Message push called but sent 0 result response.", debugMask);
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

			notifyChatUpdate(chatID);
			connection.close();
			return true;
		}
		connection.close();
		return false;
	}

	public boolean editMessage(HashMap<String, String> input)
	{
		try {
			int userID = Integer.parseInt(input.get("UserID"));
			int messageID = Integer.parseInt(input.get("MessageID"));
			String messageBody = input.get("Message");

			DatabaseConnection connection = DatabasePool.getConnection();

			HashMap<String, String> message = connection.pullMessage(messageID);
			int senderID = Integer.parseInt(message.get("SenderID"));
			int chatID = Integer.parseInt(message.get("ChatID"));

			if (senderID == userID) {

				if (connection.editMessage(messageID, messageBody)) {
					notifyChatUpdate(chatID);
				}
			} else {
				HashMap<String, String> userPair = connection.pullSingleUserChatPair(chatID, userID);
				int permissions = Integer.parseInt(userPair.get("Permissions"));

				if ((permissions & Permissions.DELETE) == Permissions.DELETE) {

					if (connection.deleteMessage(messageID)) {
						notifyChatUpdate(chatID);
					}
				}
			}
		}
		catch (NullPointerException e)
		{
			Debugger.record("Expected items missing from input to delete message: " + e.getMessage(), debugMask);
			return false;
		}
		catch (NumberFormatException e)
		{
			Debugger.record("Expected items missing from input to delete message: " + e.getMessage(), debugMask);
			return false;
		}
		return true;
	}

	/**
	 * Checks to see if the sending user has permissions
	 * to delete a single message from the chat. If so, deletes the message,
	 * and then spins up a thread to notify all users subscribed to that chat.
	 *
	 * @param input A dictionary containing the input received from the parser.
	 * @return A boolean indicating if a message has been successfully deleted.
	 */

	public boolean deleteMessage(HashMap<String, String> input)
	{
		try
		{
			int userID = Integer.parseInt(input.get("UserID"));
			int messageID = Integer.parseInt(input.get("MessageID"));

			DatabaseConnection connection = DatabasePool.getConnection();

			HashMap<String, String> message = connection.pullMessage(messageID);
			int senderID = Integer.parseInt(message.get("SenderID"));
			int chatID = Integer.parseInt(message.get("ChatID"));

			if (senderID == userID) {

				if (connection.deleteMessage(messageID)) {
					notifyChatUpdate(chatID);
				}
			}
			else
			{
				HashMap<String, String> userPair = connection.pullSingleUserChatPair(chatID, userID);
				int permissions = Integer.parseInt(userPair.get("Permissions"));

				if ((permissions & Permissions.DELETE) == Permissions.DELETE) {

					if (connection.deleteMessage(messageID)) {
						notifyChatUpdate(chatID);
					}
				}
			}
		}
		catch (NullPointerException e)
		{
			Debugger.record("Expected items missing from input to delete message: " + e.getMessage(), debugMask);
			return false;
		}
		catch (NumberFormatException e)
		{
			Debugger.record("Expected items missing from input to delete message: " + e.getMessage(), debugMask);
			return false;
		}
		return true;
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

	/**
	 * Used to ensure that passwords meet the syntax requirements.
	 * @param password The password string to check.
	 * @return A boolean indicating success or failure.
	 */
	public boolean checkPasswordSyntax(String password)
	{
		
		if (password.length() < ServerController.MIN_PASSWORD_LENGTH)
		{
			return false;
		}
		if (password.length() > ServerController.MAX_PASSWORD_LENGTH)
		{
			return false;
		}

		return true;
	}

	public void notifyChatUpdate(int chatID)
	{
		NotificationThread notifier = new NotificationThread(chatID);

		new Thread(notifier).start();
	}

	/**
	 * Handles clean up before the server thread exits.
	 */
	private void onExit()
	{

		Debugger.record("Beginning server thread exit: " + getThreadID(), debugMask);

		try
		{

			// Remove the user from the logged in user map so no attempt is made to send them notifications.
			if (user != null)
			{
				ServerController.removeLoggedInUser(user);
			}
			// Remove the session from the servercontroller set so the ID can be reused.
			if (sessionID != "NONE")
			{
				ServerController.removeSession(sessionID);
			}
			// Close the print writer for the socket.
			if (writer != null)
			{
				writer.close();
			}
			// Close the reader for the socket.
			if (reader != null)
			{
				reader.close();
			}
			// Finally, close the socket itself.
			if (socket != null)
			{
				socket.close();
			}
		}
		catch (Exception e)
		{
			Debugger.record("ServerThread encountered error upon closing: " + e.getMessage(), debugMask + 1);
		}

        return;
	}
}
