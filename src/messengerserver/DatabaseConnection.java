package messengerserver;

import jdk.nashorn.internal.runtime.Debug;

import javax.naming.SizeLimitExceededException;
import javax.xml.transform.Result;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class DatabaseConnection 
{

	
	private boolean isOccupied = false;
	private final int debugMask = 2;

	// These are all of the statements that the connection can execute.
	
	private Connection connection;
	private PreparedStatement getUserByName;
	private PreparedStatement getUserByID;
	private PreparedStatement searchUserName;
	private PreparedStatement createUser;
	private PreparedStatement deleteUser;
	private PreparedStatement pullFriends;
	private PreparedStatement addFriendRequest;
	private PreparedStatement checkFriendRequest;
	private PreparedStatement pullFriendRequests;
	private PreparedStatement addFriend;
	private PreparedStatement removeFriend;
	private PreparedStatement checkFriend;
	private PreparedStatement createChat;
	private PreparedStatement deleteChat;
	private PreparedStatement checkChatID;
	private PreparedStatement addUserChatPair;
	private PreparedStatement checkUserChatPair;
	private PreparedStatement pullUserChats;
	private PreparedStatement pullChatSubscribers;
	private PreparedStatement addMessage;
	private PreparedStatement deleteMessage;
	private PreparedStatement pullMessagesFromChat;


	
	private static final String getUserByNameQuery = "SELECT * FROM RegisteredUsers WHERE UserName = ?";
	private static final String getUserByIDQuery = "SELECT * FROM RegisteredUsers WHERE UserID = ?";
	private static final String searchUserNameQuery = "SELECT * FROM RegisteredUsers WHERE UserName LIKE ?";
	private static final String createUserQuery ="INSERT INTO RegisteredUsers (Username, PasswordHash, PasswordSalt) VALUES (?, ?, ?)";
	private static final String deleteUserQuery = "DELETE FROM RegisteredUsers WHERE UserID = ?";
	private static final String pullFriendsQuery = "SELECT * FROM FriendPairs WHERE UserID = ?";
	private static final String addFriendRequestQuery = "INSERT INTO FriendRequests(UserID, UserName, FriendUserID, FriendUserName) VALUES (?, ?, ?, ?)";
	private static final String checkFriendRequestQuery = "SELECT * FROM FriendRequests WHERE UserID = ? AND FriendUserID = ?";
	private static final String pullFriendRequestsQuery = "SELECT * FROM FriendRequests WHERE FriendUserID = ?";
	private static final String addFriendQuery = "INSERT INTO FriendPairs (UserID, FriendUserID, FriendUserName, PrivateChatID) VALUES (?, ?, ?, ?)";
	private static final String removeFriendQuery = "DELETE FROM FriendPairs WHERE UserID = ? AND FriendUserID = ?";
	private static final String checkFriendQuery = "SELECT * FROM FriendPairs WHERE UserID = ? AND FriendUserID = ?";
	private static final String createChatQuery = "INSERT INTO Chats (ChatID, OwnerID) VALUES (?, ?)";
	private static final String deleteChatQuery = "DELETE FROM Chats WHERE ChatID = ?";
	private static final String checkChatIDQuery = "SELECT * FROM Chats WHERE ChatID = ?";
	private static final String pullUserChatsQuery = "SELECT * FROM UserChatPairs WHERE UserID = ?";
	private static final String pullChatSubscribersQuery = "SELECT * FROM UserChatPairs WHERE ChatID = ?";
	private static final String addUserChatPairQuery = "INSERT INTO UserChatPairs(UserID, ChatID) VALUES (?, ?)";
	private static final String checkUserChatPairQuery = "SELECT * FROM UserChatPairs WHERE ChatID = ? AND UserID = ?";
	private static final String pullMessagesFromChatQuery = "SELECT * FROM Messages WHERE ChatID = ?";
	private static final String addMessageQuery = "INSERT INTO Messages(ChatID, SenderID, SenderName, Message) VALUES (?, ?, ?, ?)";
	private static final String deleteMessageQuery = "DELETE FROM Messages WHERE MessageID = ?";


	public DatabaseConnection(String database, String user, String pass) 
	{
		
		
		// Here we connect to the database.

		Debugger.record("Database Connection being created.", 2);
		try 
		{

			DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
			connection = DriverManager.getConnection(database, user, pass);
			Debugger.record("Database connection established", 2);
		} 
		catch (Exception e) 
		{
			Debugger.record("Database connection failed: " + Arrays.toString(e.getStackTrace()), 3);
		}
		
		
		// Here we create all the statements that the connection can execute throughout its life.
		try 
		{	
			getUserByName = connection.prepareStatement(getUserByNameQuery);
			getUserByID = connection.prepareStatement(getUserByIDQuery);
			searchUserName = connection.prepareStatement(searchUserNameQuery);
			createUser = connection.prepareStatement(createUserQuery);
			deleteUser = connection.prepareStatement(deleteUserQuery);
			pullFriends = connection.prepareStatement(pullFriendsQuery);
			addFriendRequest = connection.prepareStatement(addFriendRequestQuery);
			checkFriendRequest = connection.prepareStatement(checkFriendRequestQuery);
			pullFriendRequests = connection.prepareStatement(pullFriendRequestsQuery);
			addFriend = connection.prepareStatement(addFriendQuery);
			removeFriend = connection.prepareStatement(removeFriendQuery);
			checkFriend = connection.prepareStatement(checkFriendQuery);
			createChat = connection.prepareStatement(createChatQuery);
			deleteChat = connection.prepareStatement(deleteChatQuery);
			checkChatID = connection.prepareStatement(checkChatIDQuery);
			pullUserChats = connection.prepareStatement(pullUserChatsQuery);
			pullChatSubscribers = connection.prepareStatement(pullChatSubscribersQuery);
			addUserChatPair = connection.prepareStatement(addUserChatPairQuery);
			checkUserChatPair = connection.prepareStatement(checkUserChatPairQuery);
			pullMessagesFromChat = connection.prepareStatement(pullMessagesFromChatQuery);
			addMessage = connection.prepareStatement(addMessageQuery);
			deleteMessage = connection.prepareStatement(deleteMessageQuery);
		}
		catch (SQLException e)
		{
			Debugger.record("Statement creation error: " + e.getMessage(), 3);
		}
		
	}
	
	public boolean open() 
	{
		if (isOccupied == false) 
		{

			isOccupied = true;
			Debugger.record("A database connection has allowed access to a thread.", 2);
			return true;
		}
		else
		{
			Debugger.record("A database connection has denied access to a thread.", 3);
			return false;
		}
	}
	

	public void close() 
	{
		isOccupied = false;
	}


	//<editor-fold desc="Account-related queries">

	/**
	 * Creates a single new RegisteredUser in the database.
	 *
	 * @param username The name of the user to be created.
	 * @param passwordHash The hashed password of the user to be created.
	 * @param passwordSalt The password salt of the user to be created.
	 * @return A boolean indicating whether or not the user was created successfully.
	 */
	public boolean createUser(String username, String passwordHash, String passwordSalt) {

		if (getUser(username).size() > 0) {
			Debugger.record("Duplicate user creation attempt rejected.", debugMask);
			return false;
		}
		try {
			createUser.setString(1, username);
			createUser.setString(2, passwordHash);
			createUser.setString(3, passwordSalt);
		}
		catch(SQLException e) {
			Debugger.record("Error preparing create user statement.", debugMask + 1);
			return false;
		}
		
		try {
			createUser.execute();
			if (createUser.getUpdateCount() == 1) {
				Debugger.record("User " + username + " has been created.", debugMask);
				return true;
			}
			else {
				Debugger.record("CreateUser query was executed, but no rows were modified.", debugMask + 1);
				return false;
			}
		}
		catch(SQLException e) {
			Debugger.record("Error executing create user statement.", debugMask + 1);
			return false;
		}
	}

	public boolean deleteUser(int userID) {

		try {
			deleteUser.setInt(1, userID);
		}
		catch(SQLException e) {
			Debugger.record("Error preparing delete user by ID statement.", debugMask + 1);
			return false;
		}

		try {
			deleteUser.execute();

			if (deleteUser.getUpdateCount() > 0) {
				return true;
			}
			return false;
		}
		catch(SQLException e) {
			Debugger.record("Error executing delete user by ID statement.", debugMask + 1);
			return false;
		}
	}


	/**
	 *  This method returns a single user by their UserName.
	 *  @param username the name of the user.
	 *	@return a HashMap containing the user's database entry.
 	 */
	public HashMap<String, String> getUser(String username)
	{
		
		HashMap<String, String> userMap = new HashMap<String, String>();

		try {
			getUserByName.setString(1, username);
		}
		catch(SQLException e) {
			Debugger.record("Error preparing get user by name statement.", debugMask + 1);
			return userMap;
		}
		
		try {
			ResultSet results = getUserByName.executeQuery();
			ArrayList<HashMap<String, String>> resultsList = parseResultSet(results);

			// Only one or zero users should ever be returned by a getUser query.
			if (resultsList.size() > 1)
			{
				userMap.put("ER", "An error has occurred. Please contact the administrator.");
				Debugger.record("Error: A duplicate user has been retrieved when conducting a search by UserName.", 3);
			}
			else if (resultsList.size() == 1)
			{
				userMap = resultsList.get(0);
			}
			else
			{
				userMap = new HashMap<String, String>();
			}
			
		}
		catch(SQLException e) {
			Debugger.record("Error executing get user by name statement.", debugMask + 1);
		}
		
		return userMap;
	}


	/**
	 *  This method returns a single user by their UserID.
	 *  @param userID the ID of the user.
	 *	@return a HashMap containing the user's database entry.
	 */
	public HashMap<String, String> getUser(int userID)
	{
		HashMap<String, String> userMap = new HashMap<String, String>();
		try
		{
			getUserByID.setInt(1, userID);
		}
		
		catch(SQLException e)
		{
			Debugger.record("Error preparing get user by ID statement.", debugMask + 1);
		}
		
		try
		{
			ResultSet results = getUserByID.executeQuery();
			ArrayList<HashMap<String, String>> resultsList = parseResultSet(results);

			// Only one or zero users should ever be returned by a getUser query.
			if (resultsList.size() > 1)
			{
				Debugger.record("Error: A duplicate user has been retrieved when conducting a search by ID.", 3);
			}
			else if (resultsList.size() == 1)
			{
				userMap = resultsList.get(0);
			}
			else
			{
				userMap = new HashMap<String, String>();
			}
		}
		catch(SQLException e)
		{
			Debugger.record("Error executing get user by ID statement.", debugMask + 1);
		}
		
		return userMap;
	}


	/**
	 *  This method returns all users whose name contains the searchString.
	 *  @param searchString The string to search usernames for.
	 *	@return a HashMap containing the user's database entry.
	 */
	public ArrayList<HashMap<String, String>> searchUserName(String searchString)
	{

		Debugger.record("Searching for users with search string: " + searchString, debugMask);
		ArrayList<HashMap<String, String>> userList = new ArrayList<HashMap<String, String>>();
		try
		{
			searchUserName.setString(1, searchString + "%");

			ParameterMetaData meta = searchUserName.getParameterMetaData();

			Debugger.record("Prepared user search statement metadata: " + meta.toString(), debugMask);

		}

		catch(SQLException e)
		{
			Debugger.record("Error preparing find users by name statement: " + e.getMessage(), debugMask + 1);
		}

		try
		{
			ResultSet results = searchUserName.executeQuery();
			userList = parseResultSet(results);
		}
		catch(SQLException e)
		{
			Debugger.record("Error executing search username statement.", debugMask + 1);
		}

		return userList;
	}


	//</editor-fold>



	//<editor-fold desc="Friend-Related Queries">
	/**
	 * This method gets the list of a user's friends, stored as HashMaps.
	 * @param userID The ID of the user whose friends are being gotten.
	 * @return An ArrayList of HashMaps representing an individual user.
	 */
	public ArrayList<HashMap<String, String>> pullFriends(int userID)
	{
		ArrayList<HashMap<String, String>> friendsMap = new ArrayList<HashMap<String, String>>();

		try
		{
			pullFriends.setInt(1, userID);
		}
		catch(SQLException e)
		{
			Debugger.record("Error preparing get user by name statement.", debugMask + 1);
			return friendsMap;
		}

		try
		{
			ResultSet results = pullFriends.executeQuery();
			friendsMap = parseResultSet(results);
		}
		catch(SQLException e)
		{
			Debugger.record("Error executing get user by nane statement.", debugMask + 1);
		}
		return friendsMap;
	}


	/**
	 * This method adds a single friend request to the database.
	 * Before creating a new friend pair between UserOne and UserTwo, the server ensures that both request ends
	 * - UserOne:UserTwo and UserTwo:UserOne - have been created.
	 *
	 * @param userID The ID of the requesting user.
	 * @param friendUserID The ID of the user receiving the request.
	 * @return A boolean indicating whether or not the request was successfully added to the database.
	 */
	public boolean addFriendRequest(int userID, String username, int friendUserID)
	{

		HashMap<String, String> friend = getUser(friendUserID);

		if (checkFriend(userID, friendUserID) == false)
		{
			String friendUserName = friend.get("UserName");
			try
			{
				addFriendRequest.setInt(1, userID);
				addFriendRequest.setString(2, username);
				addFriendRequest.setInt(3, friendUserID);
				addFriendRequest.setString(4, friendUserName);
			}
			catch (Exception e)
			{
				Debugger.record("Add friend query failed due to exception.", debugMask + 1);
				return false;
			}

			try
			{
				addFriendRequest.execute();

				if (addFriendRequest.getUpdateCount() > 0)
				{
					return true;
				}
				else {
					Debugger.record("Executed addFriend query, but no friend pair was created.", debugMask + 1);
				}

			}
			catch (SQLException e)
			{
				Debugger.record("Error when executing addFriend query." + e.getMessage(), debugMask + 1);
			}
		}

		return false;

	}

	/**
	 * This method looks for a single friend request in the database.
	 * Before creating a new friend pair between UserOne and UserTwo, the server ensures that both request ends
	 * - UserOne:UserTwo and UserTwo:UserOne - have been created.
	 * When the server receives a friend request, it uses this method to check if the counterbalancing request
	 * has already been made. If it has, it calls addFriend(), adding two new friend pairs - A UserOne:UserTwo
	 * pair and a UserTwo: UserOne pair - to the database.
	 * If a counterbalancing friend request does not already exist, the server calls addFriendRequest().
	 *
	 * @param userID The ID of the requesting user.
	 * @param friendUserID The ID of the user receiving the request.
	 * @return A boolean indicating if the requested pair exists.
	 */

	public boolean checkFriendRequest(int userID, int friendUserID)
	{
		try
		{
			checkFriendRequest.setInt(1, userID);
			checkFriendRequest.setInt(2, friendUserID);
		}
		catch (Exception e)
		{
			Debugger.record("Could not set parameters for checkFriend query", debugMask + 1);
			return false;
		}
		try
		{
			ResultSet results = checkFriendRequest.executeQuery();
			ArrayList<HashMap<String, String>> resultsList = parseResultSet(results);

			if (resultsList.size() == 0)
			{
				return false;
			}
			else if (resultsList.size() == 1)
			{
				return true;
			}
			else if (resultsList.size() > 0)
			{
				throw new SizeLimitExceededException();
			}
		}
		catch (SQLException e)
		{
			Debugger.record("Error executing checkFriend query." + e.getMessage(), debugMask + 1);
		}
		catch (SizeLimitExceededException e)
		{
			Debugger.record("CheckFriend query result was unexpectedly large.", debugMask + 1);
		}

		return false;
	}

	/**
	 * This method pulls all friend requests directed towards a specific user (I.E., where the FriendUserID of
	 * the request is that user's ID).
	 * @param userID The ID of the user.
	 * @return An Arraylist of HashMaps containing all active friend requests directed at this user.
	 */
	public ArrayList<HashMap<String, String>> pullFriendRequests(int userID)
	{
		ArrayList<HashMap<String, String>> friendRequests = new ArrayList<>();
		try
		{
			pullFriendRequests.setInt(1, userID);
			ResultSet results = pullFriendRequests.executeQuery();

			friendRequests = parseResultSet(results);
		}
		catch (Exception e)
		{
			Debugger.record("Failed to pull friend requests in database connection: " + e.getMessage(), debugMask);
		}

		return friendRequests;
	}

	/**
	 * This method checks to see if two users are friends, by
	 * checking to see if the UserOne:UserTwo friend pair exists
	 * in the database. Does not check the counterbalancing
	 * UserTwo: UserOne pair.
	 *
	 * @param userOneID The ID of the first user.
	 * @param userTwoID The ID of the second user.
	 * @return A boolean indicating if the two users are friends.
	 */
	public boolean checkFriend(int userOneID, int userTwoID)
	{
		try
		{
			checkFriend.setInt(1, userOneID);
			checkFriend.setInt(2, userTwoID);
		}
		catch (Exception e)
		{
			Debugger.record("Could not set parameters for checkFriend query", debugMask + 1);
			return false;
		}
		try
		{
			ResultSet results = checkFriend.executeQuery();
			ArrayList<HashMap<String, String>> resultsList = parseResultSet(results);

			if (resultsList.size() == 0)
			{
				return false;
			}
			else if (resultsList.size() == 1)
			{
				return true;
			}
			else if (resultsList.size() > 0)
			{
				throw new SizeLimitExceededException();
			}
		}
		catch (SQLException e)
		{
			Debugger.record("Error executing checkFriend query." + e.getMessage(), debugMask + 1);
		}
		catch (SizeLimitExceededException e)
		{
			Debugger.record("CheckFriend query result was unexpectedly large.", debugMask + 1);
		}

		return false;
	}

	/**
	 * Creates two user-chat pairs identifying two users as friends, as well as
	 * a private chat only accessible to them.
	 * @param userOneID The ID of the first user.
	 * @param userTwoID The ID of the second user.
	 * @return
	 */

	public boolean addFriend(int userOneID, int userTwoID)
	{
		HashMap<String, String> userOne = getUser(userOneID);
		HashMap<String, String> userTwo = getUser(userTwoID);

		if (checkFriend(userOneID, userTwoID) == false)
		{

			String userOneName = userOne.get("UserName");
			String userTwoName = userTwo.get("UserName");
			try
			{
				int chatID = 0;
				do
				{
					chatID = Cryptographer.generateRandomInteger(8);

				} while ((chatID == 0) || checkChatID(chatID) == true);

				createChat.setInt(1, chatID);
				createChat.setInt(2, userOneID);
				createChat.execute();

				addFriend.setInt(1, userOneID);
				addFriend.setInt(2, userTwoID);
				addFriend.setString(3, userTwoName);
				addFriend.setInt(4, chatID);
				addFriend.execute();


				addFriend.setInt(1, userTwoID);
				addFriend.setInt(2, userOneID);
				addFriend.setString(3, userOneName);
				addFriend.setInt(4, chatID);
				addFriend.execute();

				return true;
			}
			catch (Exception e)
			{
				Debugger.record("Add friend query failed due to exception.", debugMask + 1);
				return false;
			}
		}

		return false;
	}

	/**
	 * Deletes two user-chat pairs identifying two users as friends.
	 * Does not delete the associated chat.
	 *
	 * @param userOneID The ID of the first user.
	 * @param userTwoID The ID of the second user.
	 * @return
	 */

	public boolean removeFriend(int userOneID, int userTwoID)
	{
		if (checkFriend(userOneID, userTwoID) == true)
		{
			try
			{
				removeFriend.setInt(1, userOneID);
				removeFriend.setInt(2, userTwoID);
				removeFriend.execute();

				removeFriend.setInt(1, userTwoID);
				removeFriend.setInt(2, userOneID);
				removeFriend.execute();

				return true;
			}
			catch (Exception e)
			{
				Debugger.record("Remove friend query failed due to exception.", debugMask + 1);
				return false;
			}
		}

		return false;
	}
	//</editor-fold>



	/**
	 * Executes a database query to create a new Chat.
	 * @param creatorID The ID of the User who will be the Owner of the chat.
	 * @return An int indicating the ChatID of the new Chat. -1 if there is an error.
	 */

	public int createChat(int creatorID)
	{

		int chatID = 0;
		try {

			do
			{
				chatID = Cryptographer.generateRandomInteger(8);
			} while ((checkChatID(chatID) == true) || (chatID == 0));

			createChat.setInt(1, chatID);
			createChat.setInt(2, creatorID);

		}
		catch(SQLException e) {
			Debugger.record("Error preparing create chat statement.", debugMask + 1);
			return -1;
		}

		try {
			createChat.execute();
			if (createChat.getUpdateCount() == 1) {
				Debugger.record("New chat has been created by user " + creatorID, debugMask);

				addUserChatPair(chatID, creatorID);

				return chatID;
			}
			else {
				Debugger.record("CreateChat query was executed, but no rows were modified.", debugMask + 1);
				return -1;
			}
		}
		catch(SQLException e) {
			Debugger.record("Error executing create chat statement." + e.getMessage(), debugMask + 1);
			return -1;
		}
	}


	/**
	 * Deletes a specific chat by ID.
	 * @param chatID The ID of the Chat to be deleted.
	 * @return
	 */
	public boolean deleteChat(int chatID) {

		Debugger.record("Deleting chat: " + chatID, debugMask + 1);
		try {
			deleteChat.setInt(1, chatID);
		}
		catch(SQLException e) {
			Debugger.record("Error preparing delete chat by ID statement.", debugMask + 1);
			return false;
		}

		try {
			deleteChat.execute();

			if (deleteChat.getUpdateCount() > 0) {
				return true;
			}
			return false;
		}
		catch(SQLException e) {
			Debugger.record("Error executing delete chat by ID statement: " + e.getMessage(), debugMask + 1);
			return false;
		}
	}

	/**
	 * This method checks for the existence of a chat by a specific ID.
	 * @param chatID The ID to check.
	 * @return True if the chat was found, false otherwise.
	 */
	public boolean checkChatID(int chatID) {

		try
		{
			checkChatID.setInt(1, chatID);
		}
		catch (Exception e)
		{
			Debugger.record("Could not set parameters for checkChatID query", debugMask + 1);
			return false;
		}
		try
		{
			ResultSet results = checkChatID.executeQuery();
			ArrayList<HashMap<String, String>> resultsList = parseResultSet(results);

			if (resultsList.size() == 0)
			{
				return false;
			}
			else if (resultsList.size() == 1)
			{
				return true;
			}
			else if (resultsList.size() > 1)
			{
				throw new SizeLimitExceededException();
			}
		}
		catch (SQLException e)
		{
			Debugger.record("Error executing checkChatID query." + e.getMessage(), debugMask + 1);
		}
		catch (SizeLimitExceededException e)
		{
			Debugger.record("checkChatID query result was unexpectedly large.", debugMask + 1);
		}

		return false;
	}

	/**
	 * Retrieves an ArrayList of all User-Chat pairs for a specific chat.
	 *
	 * @param chatID The chat whose subscribers should be retrieved.
	 * @return An ArrayList of all User-Chat pairs for the provided chat.
	 */
	public ArrayList<HashMap<String, String>> pullChatSubscribers(int chatID)
	{

		ArrayList<HashMap<String, String>> subscribers = new ArrayList<>();
		try
		{
			pullChatSubscribers.setInt(1, chatID);

			ResultSet results = pullChatSubscribers.executeQuery();

			subscribers = parseResultSet(results);
		}
		catch (Exception e)
		{
			Debugger.record("Failed to execute pullChatSubscribers query: " + e.getMessage(), debugMask);
			return subscribers;
		}

		return subscribers;
	}


	/**
	 * Creates a user-chat pair identifying the user as a subscriber to a specific chat.
	 * @param chatID The ID of the chat.
	 * @param userID The ID of the user.
	 * @return a boolean indicating success.
	 */
	public boolean addUserChatPair(int chatID, int userID)
	{
		if (checkUserChatPair(chatID, userID) == false)
		{

			try
			{
				addUserChatPair.setInt(1, userID);
				addUserChatPair.setInt(2, chatID);
			}
			catch (Exception e)
			{
				Debugger.record("addUserChatPair query with parameters ChatID: " + chatID + " UserID: " + userID + " failed due to exception.", debugMask + 1);
				return false;
			}

			try
			{
				addUserChatPair.execute();

				if (addUserChatPair.getUpdateCount() > 0)
				{
					Debugger.record("New chat-user pair created with parameters:\n Chat: " + chatID + "\nUser: " + userID, debugMask);
					return true;
				}
				else {
					Debugger.record("Executed addUserChatPair query, but no  user-chat pair was created.", debugMask + 1);
				}

			}
			catch (SQLException e)
			{
				Debugger.record("Error when executing addUserChatPair query with parameters ChatID: " + chatID + " UserID: " + userID + ". " + e.getMessage(), debugMask + 1);
			}
		}

		return false;
	}

	/**
	 * Checks to see if a particular user-chat pair exists.
	 * @param chatID The ID of the chat.
	 * @param userID The ID of the user.
	 * @return A boolean indicating whether or not the pair was located.
	 */
	public boolean checkUserChatPair(int chatID, int userID)
	{
		try
		{
			checkUserChatPair.setInt(1, chatID);
			checkUserChatPair.setInt(2, userID);
		}
		catch (Exception e)
		{
			Debugger.record("Could not set parameters for checkUserChatPair query", debugMask + 1);
			return false;
		}
		try
		{
			ResultSet results = checkUserChatPair.executeQuery();
			ArrayList<HashMap<String, String>> resultsList = parseResultSet(results);

			if (resultsList.size() == 0)
			{
				return false;
			}
			else if (resultsList.size() == 1)
			{
				return true;
			}
			else
			{
				throw new SizeLimitExceededException();
			}
		}
		catch (SQLException e)
		{
			Debugger.record("Error executing checkUserChatPair query." + e.getMessage(), debugMask + 1);
		}
		catch (SizeLimitExceededException e)
		{
			Debugger.record("checkUserChatPair query result was unexpectedly large.", debugMask + 1);
		}

		return false;
	}


	/**
	 * Pulls all user-chat pairs associated with a specific user.
	 * @param userID The ID of the user.
	 * @return A list of HashMaps storing the chat pairs.
	 */

	public ArrayList<HashMap<String, String>> pullUserChats(int userID)
	{

		ArrayList<HashMap<String, String>> chatsList = new ArrayList<HashMap<String, String>>();

		try
		{
			pullUserChats.setInt(1, userID);
		} catch (SQLException e) {
			Debugger.record("Error preparing pull user chats statement.", debugMask + 1);
			return chatsList;
		}

		try
		{
			ResultSet results = pullUserChats.executeQuery();
			chatsList = parseResultSet(results);

		} catch (SQLException e)
		{
			Debugger.record("Error executing pull user chats statement.", debugMask + 1);
		}

		return chatsList;
	}

	/**
	 * Pulls all messages assigned a specific chatID (I.E., from a single chat).
	 * @param chatID The ID of the Chat to pull messages from.
	 * @return A list of HashMaps storing the messages.
	 */

	public ArrayList<HashMap<String, String>> pullMessagesFromChat(int chatID)
	{

		ArrayList<HashMap<String, String>> messageList = new ArrayList<HashMap<String, String>>();

		try
		{
			pullMessagesFromChat.setInt(1, chatID);
		} catch (SQLException e) {
			Debugger.record("Error preparing pull messages from chat statement.", debugMask + 1);
			return messageList;
		}

		try {
			ResultSet results = pullMessagesFromChat.executeQuery();
			messageList = parseResultSet(results);

		} catch (SQLException e) {
			Debugger.record("Error executing pull message from chat statement.", debugMask + 1);
		}

		return messageList;
	}

	/**
	 * Creates a message assigned to a specific ChatID.
	 * @param chatID The ID of the chat.
	 * @param senderID The ID of the user who sent the message.
	 * @param message The text body of the message itself.
	 * @return A boolean indicating success.
	 */

	public boolean addMessage(int chatID, int senderID, String senderName, String message)
	{
		try
		{
			addMessage.setInt(1, chatID);
			addMessage.setInt(2, senderID);
			addMessage.setString(3, senderName);
			addMessage.setString(4, message);
		}
		catch (Exception e)
		{
			Debugger.record("AddMessage query failed due to exception.", debugMask + 1);
			return false;
		}

		try
		{
			addMessage.execute();

			if (addMessage.getUpdateCount() > 0)
			{
				System.out.println("A new message has been added to chat " + chatID);
				Debugger.record("A new message has been added to chat " + chatID, debugMask);
				return true;
			}
			else {
				Debugger.record("Executed addMessage query, but no message was created.", debugMask + 1);
			}

		}
		catch (SQLException e)
		{
			Debugger.record("Error when executing addMessage query." + e.getMessage(), debugMask + 1);
		}

		return false;
	}

	/**
	 * Deletes a specific message.
	 * @param messageID The ID of the message to be deleted.
	 * @return A boolean indicating success.
	 */
	public boolean deleteMessage(int messageID) {

		try {
			deleteMessage.setInt(1, messageID);
		}
		catch(SQLException e) {
			Debugger.record("Error preparing delete message by ID statement.", debugMask + 1);
			return false;
		}

		try {
			deleteMessage.execute();

			if (deleteMessage.getUpdateCount() > 0) {
				return true;
			}
			return false;
		}
		catch(SQLException e) {
			Debugger.record("Error executing delete message by ID statement.", debugMask + 1);
			return false;
		}
	}


	/**
	 * Gets the status of this DatabaseConnection - whether it is currently occupied or not.
	 * @return The isOccupied boolean.
	 */
	public boolean getStatus() 
	{
		return isOccupied;
	}

	

	/**
	 * This method takes a ResultSet and converts it into a list of HashMaps.
	 * @param results The ResultSet to parse.
	 * @return A list of HashMap<String, String> containing the rows of the ResultSet.
	 */
	public ArrayList<HashMap<String, String>> parseResultSet(ResultSet results) 
	{
		ArrayList<HashMap<String, String>> resultsList = new ArrayList<HashMap<String, String>>();
		ArrayList<String> columns = new ArrayList<String>();

		try 
		{
			// Ensure there is at least one entry in the set.
			if (results.next() == false) {
				Debugger.record("Empty result set provided to parser.", debugMask);
				return resultsList;
			}

			// Getting the column names.
			
			ResultSetMetaData metaData = results.getMetaData();
		
			for (int i = 1; i < metaData.getColumnCount() + 1; i++)
			{
				columns.add(metaData.getColumnName(i));
			}
		}
		catch (Exception e) 
		{
			Debugger.record("Error while getting result set column names.", debugMask + 1);
			return resultsList;
		}
		
		// Building a List of hashmaps out of the entries.
		try 
		{ 
			do
			{
				HashMap<String, String> resultsMap = new HashMap<String, String>();
				
				for (int i = 0; i < columns.size(); i++)
				{

					String data = results.getString(columns.get(i));

					resultsMap.put(columns.get(i), data);
				}
				resultsList.add(resultsMap);
			} while (results.next() == true);
			
		}
		catch (Exception e) {

			Debugger.record("Error while turning rows into hash maps.", debugMask + 1);
		}

		return resultsList;
		
	}

	/**
	 * This method creates a set of database items for testing.
	 */
	public void createTestItems()
	{
		try {
			PreparedStatement newUser = connection.prepareStatement("INSERT INTO RegisteredUsers(UserID, UserName, PasswordHash, PasswordSalt) VALUES (?, ?, ?, ?)");
			PreparedStatement newFriendPair = connection.prepareStatement("INSERT INTO FriendPairs(UserID, FriendUserID, FriendUserName, PrivateChatID) VALUES (?, ?, ?, ?)");
			PreparedStatement newFriendRequest = connection.prepareStatement("INSERT INTO FriendRequests(UserID, UserName, FriendUserID, FriendUserName) VALUES (?, ?, ?, ?)");

			PreparedStatement newChat = connection.prepareStatement("INSERT INTO Chats(ChatID, OwnerID) VALUES (?, ?)");
			PreparedStatement newUserChatPair = connection.prepareStatement("INSERT INTO UserChatPairs(UserID, ChatID) VALUES (?, ?)");
			PreparedStatement newMessage = connection.prepareStatement("INSERT INTO Messages(MessageID, ChatID, SenderID, Message) VALUES (?, ?, ?, ?)");


			// Creating test users.
			newUser.setInt(1, 4);
			newUser.setString(2, "testuserOne");
			newUser.setString(3, "testpass");
			newUser.setString(4, "salty");
			newUser.execute();

			newUser.setInt(1, 5);
			newUser.setString(2, "testuserTwo");
			newUser.setString(3, "testpass");
			newUser.setString(4, "salty");
			newUser.execute();

			newUser.setInt(1, 6);
			newUser.setString(2, "testuserThree");
			newUser.setString(3, "testpass");
			newUser.setString(4, "salty");
			newUser.execute();

			newUser.setInt(1, 7);
			newUser.setString(2, "testuserFour");
			newUser.setString(3, "testpass");
			newUser.setString(4, "salty");
			newUser.execute();

			// Creating friend pairs between testuserOne and testuserTwo, and testuserOne and testuserThree.

			// Create the private chat
			newChat.setInt(1, 2);
			newChat.setInt(2, 4);
			newChat.execute();
			// Creating the two pairs for testuserOne and testuserTwo
			newFriendPair.setInt(1, 4);
			newFriendPair.setInt(2, 5);
			newFriendPair.setString(3, "testuserTwo");
			newFriendPair.setInt(4, 2);
			newFriendPair.execute();

			newFriendPair.setInt(1, 5);
			newFriendPair.setInt(2, 4);
			newFriendPair.setString(3, "testuserOne");
			newFriendPair.setInt(4, 2);
			newFriendPair.execute();

			// Creating the two pairs for testuserOne and testuserThree
			newFriendPair.setInt(1, 4);
			newFriendPair.setInt(2, 6);
			newFriendPair.setString(3, "testuserThree");
			newFriendPair.setInt(4, 3);
			newFriendPair.execute();

			newFriendPair.setInt(1, 6);
			newFriendPair.setInt(2, 4);
			newFriendPair.setString(3, "testuserOne");
			newFriendPair.setInt(4, 3);
			newFriendPair.execute();

			// Creating new friend request for testuserOne.
			newFriendRequest.setInt(1, 7);
			newFriendRequest.setString(2, "testuserFour");
			newFriendRequest.setInt(3, 4);
			newFriendRequest.setString(4, "testUserOne");
			newFriendRequest.execute();

			// Creating a test chat owned by testuserOne.
			newChat.setInt(1, 1);
			newChat.setInt(2, 4);
			newChat.execute();

			// Creating the user-chat pair for chat one and testuserOne.
			newUserChatPair.setInt(1, 4);
			newUserChatPair.setInt(2, 1);
			newUserChatPair.execute();

			// Creating the user-chat pairs for chat two and testuserOne and testUserTwo.
			newUserChatPair.setInt(1, 4);
			newUserChatPair.setInt(2, 2);
			newUserChatPair.execute();

			newUserChatPair.setInt(1, 5);
			newUserChatPair.setInt(2, 2);
			newUserChatPair.execute();

			// Creating several test messages for chat one.

			newMessage.setInt(1, 1); // Message ID
			newMessage.setInt(2, 1); // Chat ID is 1
			newMessage.setInt(3, 4); // UserID is 4 - testuserOne
			newMessage.setString(4, "testuserOne"); // Username
			newMessage.setString(5, "Test message one.");
			newMessage.execute();

			newMessage.setInt(1, 2); // Message ID
			newMessage.setInt(2, 1); // Chat ID is 1
			newMessage.setInt(3, 4); // UserID is 4 - testuserOne
			newMessage.setString(4, "testuserOne"); // Username
			newMessage.setString(5, "Test message two.");
			newMessage.execute();

			newMessage.setInt(1, 3); // Message ID
			newMessage.setInt(2, 1); // Chat ID is 1
			newMessage.setInt(3, 4); // UserID is 4 - testuserOne
			newMessage.setString(4, "testuserOne"); // Username
			newMessage.setString(5, "Test message three.");
			newMessage.execute();

			newMessage.setInt(1, 4); // Message ID
			newMessage.setInt(2, 1); // Chat ID is 1
			newMessage.setInt(3, 5); // UserID is 5 - testuserTwo
			newMessage.setString(4, "testuserTwo"); // Username
			newMessage.setString(5, "Test message four, sent by testuserTwo.");
			newMessage.execute();

			// Creating several test messages for chat three.

			newMessage.setInt(1, 1); // Message ID
			newMessage.setInt(2, 3); // Chat ID
			newMessage.setInt(3, 4); // UserID is 4 - testuserOne
			newMessage.setString(4, "testuserOne"); // Username
			newMessage.setString(5, "Test message one, chat three.");
			newMessage.execute();

			newMessage.setInt(1, 2); // Message ID
			newMessage.setInt(2, 3); // Chat ID
			newMessage.setInt(3, 4); // UserID is 4 - testuserOne
			newMessage.setString(4, "testuserOne"); // Username
			newMessage.setString(5, "Test message two, chat three.");
			newMessage.execute();

			newMessage.setInt(1, 3); // Message ID
			newMessage.setInt(2, 3); // Chat ID
			newMessage.setInt(3, 4); // UserID is 4 - testuserOne
			newMessage.setString(5, "testuserOne"); // Username
			newMessage.setString(4, "Test message three, chat three.");
			newMessage.execute();

			newMessage.setInt(1, 4); // Message ID
			newMessage.setInt(2, 3); // Chat ID
			newMessage.setInt(3, 5); // UserID is 5 - testuserTwo
			newMessage.setString(4, "testuserOne"); // Username
			newMessage.setString(5, "Test message four, chat three, sent by testuserTwo.");

			newMessage.execute();

		}
		catch (SQLException e)
		{
			Debugger.record("Error when creating database test cases: " + e.getMessage(), debugMask + 1);
		}


	}

	public void destroyTestItems()
	{

		try {
			PreparedStatement deleteUser = connection.prepareStatement("DELETE FROM RegisteredUsers WHERE UserID > 3");
			PreparedStatement deleteFriendPair = connection.prepareStatement("DELETE FROM FriendPairs WHERE UserID > 3");
			PreparedStatement deleteChat = connection.prepareStatement("DELETE FROM Chats WHERE ChatID > 0");

			deleteUser.execute();
			deleteFriendPair.execute();
			deleteChat.execute();
		}
		catch (SQLException e)
		{
			Debugger.record("Error when creating database test cases: " + e.getMessage(), debugMask + 1);
		}
	}
}
