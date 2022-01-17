package messengerserver;

public class Debugger {

	// Determines what is printed in the console. 
	// Bits assigned to incoming message type for this application are:
	// Bit 0 = Error message flag. - 1
	// Bit 1 = Database connection message flag. 
	// Bit 2 = Server thread message flag. 
	// Bit 3 = Parser thread message flag. 
	// Bit 4 = Security-related message flag.
	 
	
	private static final int printMask = 30;
	
	
	public static synchronized void print(String message) 
	{
		System.out.println(message);
	}
	
	public static synchronized void record(String message, int mask) 
	{
		
		if ((mask & printMask) > 0)
		{
			System.out.println(message);
		}
		
	}

}
