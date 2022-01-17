package messengerserver;

import java.io.FileOutputStream;
import java.util.Properties;

public class Server {

	public static void main(String[] args) 
	{
		System.out.println("Testing.");

		System.out.println("Starting server.");

		ServerController serverController = new ServerController();
		new Thread(serverController).start();

		return;
	}
}
