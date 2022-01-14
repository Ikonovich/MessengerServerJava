package messengerserver;

import com.google.gson.Gson;
import com.mysql.cj.Session;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class stores the data associated with a Registered User that is currently logged in
 * to the server.
 *
 * Most importantly, this server stores a reference to the PrintWriter associated with a user,
 * which allows communication across the user's associated socket.
 *
 */
public class RegisteredUser
{
    private String userIDstr; // Stored as a string here for efficiency.
    private int userID;
    private String username;
    private String sessionID;
    private PrintWriter writer;

    private int debugMask = 4; // The bitmask used to sort this class' debug messages.

    /**
     * The constructor for the user class.
     * @param userIDin The associated user's ID.
     * @param sessionIDin The associated user's login session.
     * @param writerIn The associated user's socket PrintWriter.
     */
    public RegisteredUser(int userIDin, String sessionIDin, PrintWriter writerIn)
    {
        userIDstr = String.valueOf(userIDin);
        userID = userIDin;
        sessionID = sessionIDin;
        writer = writerIn;
    }


    /**
     * @return The user's ID as an integer.
     */
    public int getUserIDint()
    {
        return userID;
    }

    /**
     * @return The user's ID as a string.
     */
    public String getUserIDstr()
    {
        return userIDstr;
    }

    /**
     * @return The user's ID as an integer.
     */

    public String getUsername()
    {
        return username;
    }

    /**
     * @return The user's login session ID.
     */
    public String getSessionID()
    {
        return sessionID;
    }

    /**
     * Allows another server thread to induce this user to update its friends.
     * @return A boolean indicating if the pull was successful.
     */
    public void pushFriends()
    {
       DatabaseConnection connection = DatabasePool.getConnection();

       ArrayList<HashMap<String, String>> friends = connection.pullFriends(this.userID);

       Gson json = new Gson();

       String friendString = json.toJson(friends);

       String transmitString = "FP" + Parser.pack(userID, ServerController.USER_ID_LENGTH) + this.sessionID + friendString;


       sendTransmission(transmitString);
    }

    /**
     * Writes to the user's PrintWriter, effectively transmitting a message.
     * Synchronized to prevent garbage from being transmitted over the socket.
     *
     * @param message The message to be transmitted.
     * @return A boolean indicating if the transmission was successful.
     */
    public synchronized void sendTransmission(String message)
    {
        Debugger.record("A message is being transmitted via the RegisteredUser class: " + message + "\n", debugMask);

        ServerThread.transmit(message, writer);

    }

}
