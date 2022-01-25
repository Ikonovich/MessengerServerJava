package messengerserver;


import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;

// Enumeration for user permissions


public class ServerController implements Runnable
{
	// Constants storing expected lengths of each packet section, when present.

	public static final int PORT = 4269;


	public static final int USER_ID_LENGTH = 32;
	public static final int MIN_USERNAME_LENGTH = 8;
	public static final int MAX_USERNAME_LENGTH = 32;
	public static final int MIN_PASSWORD_LENGTH = 8;
	public static final int MAX_PASSWORD_LENGTH = 128;
	public static final int SESSION_ID_LENGTH = 32;
	public static final int CHAT_ID_LENGTH = 8;
	public static final int MESSAGE_ID_LENGTH = 32;

	// Server configuration

	public static final int PACKET_SIZE = 1024; // Determines the maximum size of transmitted packets.

	// Keeps track of information for sending heartbeats (keep-alive signals), in milliseconds.
	public static final long MAX_TIMEOUT = 100000;
	public static final long HEARTBEAT_WAIT = 45000; // How long after the last read we should wait before sending a heartbeat.
	public static final long HEARTBEAT_INTERVAL = 45000; // How long we should wait between heartbeats.

	private static HashSet<String> sessionSet; // Stores all currently active session IDs.

	// Stores all logged-in users, associated with their UserID.
	// The user ID is stored as a string for efficiency.
	private static HashMap<String, RegisteredUser> loggedInUsers;

	private static int debugMask = 4;

	static
	{
		loggedInUsers = new HashMap<String, RegisteredUser>();
		sessionSet = new HashSet<String>();
	}
	
	public void run() {
		
		listen();
	}
	
	private static void listen() 
	{
		
		ServerSocket serverSocket;
		
		try 
		{
			
			boolean run = true;
			serverSocket = new ServerSocket(PORT);
			
			while(run == true) 
			{
				
				Socket socket = serverSocket.accept();
				
				ServerThread thread = new ServerThread(socket);
				
				new Thread(thread).start();
				
			}
			serverSocket.close();
		}
		catch (Exception e) 
		{
			Debugger.print(e.getMessage());
			return;
		}
	}
	
	public static synchronized boolean addLoggedInUser(RegisteredUser user)
	{
		String userID = user.getUserIDstr();

		loggedInUsers.put(userID, user);
		return true;
	}
	
	public static synchronized boolean removeLoggedInUser(RegisteredUser user)
	{

		String userID = user.getUserIDstr();
		if (loggedInUsers.containsKey(userID))
		{
			loggedInUsers.remove(userID);
			Debugger.record("A user was removed from the loggedInUsers map", debugMask);
			return true;
		}
		else {
			Debugger.record("A user was removed from the loggedInUsers map, but the user was not found.", debugMask + 1);
			return false;
		}
	}

	public static synchronized HashMap<String, RegisteredUser> getLoggedInUsers()
	{
		return loggedInUsers;
	}

	public static synchronized boolean addSession(String sessionID)
	{
		return sessionSet.add(sessionID);
	}

	public static synchronized boolean removeSession(String sessionID)
	{
		return sessionSet.remove(sessionID);
	}

}
