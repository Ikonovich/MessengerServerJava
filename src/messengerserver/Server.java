package messengerserver;

public class Server {

	private static final int debugMask = 2;

	public static void main(String[] args) 
	{

		try
		{
			Class.forName("org.mariadb.jdbc.Driver");
		}
		catch (Exception e)
		{
			System.out.println("Error");
		}
		System.out.println("Starting server.");

		ServerController serverController = new ServerController();
		new Thread(serverController).start();

		return;
	}
}
