package messengerserver;

import java.io.FileOutputStream;
import java.util.Properties;

public class Server {

	private static final int debugMask = 2;

	public static void main(String[] args) 
	{
		try
		{
			Class.forName("com.mysql.jdbc.Driver");
		}
		catch (Exception e)
		{
			Debugger.record("Error", debugMask);
		}

		System.out.println("Testing.");

		System.out.println("Starting server.");

		ServerController serverController = new ServerController();
		new Thread(serverController).start();

		return;
	}
}
