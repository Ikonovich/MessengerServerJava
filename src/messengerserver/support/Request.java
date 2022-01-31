package messengerserver.support;

public class Request {

    public int senderID;
    public String senderName;
    public int receiverID;
    public int receiverName;
    public String requestType;

    // Optional

    public int chatID = 0;
    public String chatName = "NONE";

    // Creates the objects type as a friend request.

    public Request(int senderID, String senderName, int receiverID, int receiverName) {
        this.senderID = senderID;
        this.senderName = senderName;
        this.receiverID = receiverID;
        this.receiverName = receiverName;
    }

    // Sets the objects type as a chat request.

    public Request(int senderID, String senderName, int receiverID, int receiverName, int chatID, int chatName) {
        this.senderID = senderID;
        this.senderName = senderName;
        this.receiverID = receiverID;
        this.receiverName = receiverName;
    }
}
