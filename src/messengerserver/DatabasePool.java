package messengerserver;

import java.util.ArrayList;

public class DatabasePool {
	
	private static ArrayList<DatabaseConnection> connectionList = new ArrayList<DatabaseConnection>();
	
	
	final static int MAX_CONNECTIONS = 10;
	
	private static final String DB_URL = "jdbc:mysql://localhost:3306/messenger_database";
	private static final String USER = "root";
	private static final String PASS = "giga321";
	
			
	public static synchronized DatabaseConnection getConnection() 
	{
		
		DatabaseConnection newConnection = null;

		while (newConnection == null) 
		{
			for (int i = 0; i < connectionList.size(); i++)
			{
				DatabaseConnection tempConnection = connectionList.get(i);
				
				if (tempConnection.getStatus() == false) 
				{
	
					newConnection = tempConnection;
					
				}
			}
			
			if ((newConnection == null) && (connectionList.size() < MAX_CONNECTIONS)) 
			{
				
				newConnection = newConnection();
			}
		}
		
		Debugger.record("Database pool returning connection.", 2);
		newConnection.open();
		return newConnection;
	}
	
	private static DatabaseConnection newConnection() 
	{
		
		DatabaseConnection newConnection = new DatabaseConnection(DB_URL, USER, PASS);
		
		connectionList.add(newConnection);
		return newConnection;
		
	}
	
}
