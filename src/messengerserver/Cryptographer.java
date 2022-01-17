package messengerserver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Cryptographer {

	private static final int debugMask = 8;
	
	private static SecureRandom rand;
	private static final String HASH_ALGO = "SHA-256";
	
	static {
		
		try {
			rand = SecureRandom.getInstanceStrong();
		}
		catch (NoSuchAlgorithmException e) {
			
			Debugger.record("Random number generator creation failed.", 17);
		}
	}
	
	public static String generateRandomString(int length) {
		
		String randString = "";
		
		while (randString.length() < length) {
			
			int randInt = rand.nextInt(95);
			char nextChar = (char)(randInt + 32);
			randString += nextChar;
			
		}
		
		return randString;
	}

	/**
	 * Returns a random integer within the length provided.
	 * If the provided parameters are outside the acceptable range, defaults to the closest acceptable
	 * integer rather than returning an error.
	 *
	 * @param length An int between 1 and 9.
	 * @return A random integer of the length provided.
	 */
	public static int generateRandomInteger(int length) {

		if (length > 9) {
			length = 9;
		}
		else if (length < 1) {
			length = 1;
		}

		String randString = "";

		while (randString.length() < length) {
			int randInt = rand.nextInt(10);
			randString += String.valueOf(randInt);
		}

		return Integer.parseInt(randString);
	}

	/**
	 * Used to hash a password + securely generated salt using the algorithm defined at HASH_ALGO.
	 * The hash is converted into a Base 64 string for easy database storage.
	 * @param password The password to be hashed.
	 * @param salt The cryptographically secure salt generated to assist with the hash.
	 * @return The result of the hashing algorithm.
	 */
	public static String hashPassword(String password, String salt) {

		String input = password + salt;
		String hashString;

		try
		{
			MessageDigest digest = MessageDigest.getInstance(HASH_ALGO);
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

			hashString = Base64.getEncoder().encodeToString(hash);

			System.out.println("Provided string: " + password + "\nProvided salt: " + salt + "\nResulting hash:" + hashString);


		}
		catch (Exception e)
		{
			Debugger.record("Failed to generate password hash.", debugMask + 1);
			return "";
		}
		 return hashString;
	}


	/**
	 * Takes a provided password and the user's password hash and salt from the database, and ensures
	 * that, when hashed, the provided password matches the known database hash.
	 * As a failsafe, it also ensures that the hash is not equal to the empty string,
	 * which is returned by the hashing function in case of an exception.
	 *
	 * @param passwordGiven The password provided by the user.
	 * @param passwordHash The known hash of the correct password from the database.
	 * @param passwordSalt The previously generated salt for the hashing algorithm, from the database.
 	 */
	public static boolean verifyPassword(String passwordGiven, String passwordHash, String passwordSalt) {

		String checkHash = hashPassword(passwordGiven, passwordSalt);

		if (!passwordHash.equals("") && checkHash.equals(passwordHash))
		{
			return true;
		}
		return false;
	}
}
