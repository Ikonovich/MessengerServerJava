package messengerserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NotificationThread implements Runnable
{
    int chatID = 0; // Stores the chat whose members are to be notified.

    int debugMask = 4;

    public NotificationThread(int chatID) { this.chatID = chatID; }

    public void run()
    {
        Debugger.record("Starting notification thread " + getThreadID(), debugMask);
        notifyUsers();
    }

    public long getThreadID()
    {
        return Thread.currentThread().getId();
    }

    /**
     * Sends a notification to all users subscribed to the chat provided in the constructor,
     * informing them that a new message has been sent to that particular chat.
     */
    public void notifyUsers()
    {
        DatabaseConnection connection = DatabasePool.getConnection();
        ArrayList<HashMap<String, String>> subscribers = connection.pullChatSubscribers(chatID);

        if (subscribers.size() > 0)
        {
            HashMap<String, RegisteredUser> loggedInUsers = ServerController.getLoggedInUsers();

            StringBuilder builder;
            String chatIDstr = Parser.pack(chatID, ServerController.CHAT_ID_LENGTH);

            for (Map.Entry<String, RegisteredUser> entry : loggedInUsers.entrySet())
            {
                String userID = entry.getKey();

                if (loggedInUsers.containsKey(userID))
                {
                    // Building the notification.
                    RegisteredUser user = loggedInUsers.get(userID);

                    builder = new StringBuilder("CN");
                    builder.append(Parser.pack(user.getUserIDstr(), ServerController.MAX_USERNAME_LENGTH));
                    builder.append(user.getSessionID());
                    builder.append(chatIDstr);

                    // Writing to the user's socket via their print writer.
                    user.sendTransmission(builder.toString());
                }
            }
        }

        Debugger.record("Exiting notification thread.", debugMask);
        connection.close();
    }
}
