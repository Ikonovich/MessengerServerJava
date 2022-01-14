package messengerserver;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConnectionTest {

    private static DatabaseConnection connection;

    private static int createdChatOne = 0; // Store chatID of newly created chat for later deletion.
    private static int createdChatTwo = 0; // Store chatID of newly created chat for later deletion.
    private static int createdChatThree = 0; // Store chatID of newly created chat for later deletion.



    @BeforeAll
    public static void setUpBeforeClass() {
        connection = DatabasePool.getConnection();

        connection.createTestItems();

        connection.createUser("deleteuser", "password", "salty");

        createdChatOne = connection.createChat("Test chat Six", 6, "No creator");
        createdChatThree = connection.createChat("Test chat 5", 5, "No creator");


        System.out.println("Created new chat: " + createdChatOne);

    }

    @AfterAll
    public static void tearDownAfterClass() {

        connection.deleteChat((createdChatOne));
        connection.deleteChat((createdChatTwo));
        connection.deleteChat((createdChatThree));

        //connection.destroyTestItems();

        connection.close();
    }

    // This test creates and then gets a user. As such, it technically tests two aspects of the system
    // at once, and is not applicable until getUser passes.


    // The next two tests pull a pre-created user from the database.

    @Test
    void getUserByIDShouldReturnPremadeUser()
    {

        connection = DatabasePool.getConnection();

        String userID = "3";
        String username = "testdude";
        String createTimestamp = "2021-12-22 04:47:47";
        String modifiedTimestamp = null;
        String passwordHash = "testpass";
        String passwordSalt = "1bYJiCe,kD:D&RI4p/n,e fE^\\G|HZb>70dx8y/@^Sr ]$%$M_cG}9jQAiFl\"Qv&VA7g$iyr9^&viRs%\\BH-+Z<!N:ujYuS@qp*F`!sD!A>TVOemySL7^.U<bqRL62M";

        HashMap<String, String> user = connection.getUser(Integer.parseInt(userID));

        assertTrue(user.containsKey("UserID"));
        assertTrue(user.containsKey("UserName"));
        assertTrue(user.containsKey("CreateTimestamp"));
        assertTrue(user.containsKey("ModifiedTimestamp"));
        assertTrue(user.containsKey("PasswordHash"));
        assertTrue(user.containsKey("PasswordSalt"));

        assertEquals(user.get("UserID"), userID);
        assertEquals(user.get("UserName"), username);
        assertEquals(user.get("CreateTimestamp"), createTimestamp);
        assertEquals(user.get("ModifiedTimestamp"), modifiedTimestamp);
        assertEquals(user.get("PasswordHash"), passwordHash);
        assertEquals(user.get("PasswordSalt").trim(), passwordSalt);

        connection.close();
    }


    @Test
    void getUserByNameShouldReturnPremadeUser()
    {
        connection = DatabasePool.getConnection();

        String userID = "3";
        String username = "testdude";
        String createTimestamp = "2021-12-22 04:47:47";
        String modifiedTimestamp = null;
        String passwordHash = "testpass";
        String passwordSalt = "1bYJiCe,kD:D&RI4p/n,e fE^\\G|HZb>70dx8y/@^Sr ]$%$M_cG}9jQAiFl\"Qv&VA7g$iyr9^&viRs%\\BH-+Z<!N:ujYuS@qp*F`!sD!A>TVOemySL7^.U<bqRL62M";

        HashMap<String, String> user = connection.getUser(username);

        assertTrue(user.containsKey("UserID"));
        assertTrue(user.containsKey("UserName"));
        assertTrue(user.containsKey("CreateTimestamp"));
        assertTrue(user.containsKey("ModifiedTimestamp"));
        assertTrue(user.containsKey("PasswordHash"));
        assertTrue(user.containsKey("PasswordSalt"));

        assertEquals(user.get("UserID"), userID);
        assertEquals(user.get("UserName"), username);
        assertEquals(user.get("CreateTimestamp"), createTimestamp);
        assertEquals(user.get("ModifiedTimestamp"), modifiedTimestamp);
        assertEquals(user.get("PasswordHash"), passwordHash);
        assertEquals(user.get("PasswordSalt").trim(), passwordSalt);

        connection.close();
    }


    // The next two tests create and then delete a user.

    @Test
    void createUserThenGetUserByNameShouldReturnCreatedUser()
    {

        connection = DatabasePool.getConnection();

        String username = "tempuser";
        String passwordHash = "testpass";
        String passwordSalt = "1bYJiCe,kD:D&RI4p/n,e fE^\\G|HZb>70dx8y/@^Sr ]$%$M_cG}9jQAiFl\"Qv&VA7g$iyr9^&viRs%\\BH-+Z<!N:ujYuS@qp*F`!sD!A>TVOemySL7^.U<bqRL62M";

        connection.createUser(username, passwordHash, passwordSalt);

        HashMap<String, String>user = connection.getUser(username);

        assertTrue(user.containsKey("UserID"));
        assertTrue(user.containsKey("UserName"));
        assertTrue(user.containsKey("CreateTimestamp"));
        assertTrue(user.containsKey("ModifiedTimestamp"));
        assertTrue(user.containsKey("PasswordHash"));
        assertTrue(user.containsKey("PasswordSalt"));

        assertEquals(username, user.get("UserName"));
        assertEquals(passwordHash, user.get("PasswordHash"));
        assertEquals(passwordSalt, user.get("PasswordSalt"));

        connection.close();
    }


    @Test
    void deleteUserThenGetUserByNameShouldReturnNoUser()
    {
        String username = "deleteUser";
        connection = DatabasePool.getConnection();
        HashMap<String, String> user = connection.getUser(username);

        int userID = Integer.parseInt(user.get("UserID"));

        assertTrue(connection.deleteUser(userID));


        user = connection.getUser(username);
        assertFalse(user.containsKey("UserID"));

        connection.close();
    }


    @Test
    void checkFriendOnNonexistentPairShouldReturnFalse()
    {
        connection = DatabasePool.getConnection();

        int userID = 3; // ID of user "testdude"
        int friendUserID = 29; // ID of user "testperson"

        assertFalse(connection.checkFriend(userID, friendUserID));

        connection.close();
    }


    @Test
    void checkFriendOnPremadePairShouldReturnTrue()
    {

        connection = DatabasePool.getConnection();

        int userID = 4; // ID of user "testuserOne"
        int friendUserID = 5; // ID of user "testuserTwo"

        assertTrue(connection.checkFriend(userID, friendUserID));

        connection.close();
    }


    @Test
    void checkFriendOnNewPairShouldReturnTrue() {

        connection = DatabasePool.getConnection();

        int userID = 5; // ID of user "testuserTwo"
        int friendUserID = 6; // ID of user "testuserThree"

        connection.removeFriend(userID, friendUserID);

        assertTrue(connection.addFriend(userID, friendUserID));
        assertTrue(connection.checkFriend(userID, friendUserID));

        //connection.removeFriend(userID, friendUserID);

        connection.close();
    }


    @Test
    void checkFriendOnDeletedPairShouldReturnFalse() {

        connection = DatabasePool.getConnection();

        int userID = 4; // ID of user "testuserTwo"
        int friendUserID = 6; // ID of user "testuserThree"

        connection.addFriend(userID, friendUserID);
        assertTrue(connection.removeFriend(userID, friendUserID));
        assertFalse(connection.checkFriend(userID, friendUserID));

        connection.close();
    }

    @Test
    void createChatShouldReturnPositive() {

        connection = DatabasePool.getConnection();

        int creatorID = 6; // ID of user "testuserThree"
        int chatID = connection.createChat("Testy chat", creatorID, "No Creator");

        assertTrue(chatID > 0);
        createdChatTwo = chatID;

        connection.close();
    }

    @Test
    void pullUserChatsShouldReturnPremadePair()
    {
        connection = DatabasePool.getConnection();

        int userID = 4;
        ArrayList<HashMap<String, String>> userChats = connection.pullUserChats(userID);

        assertTrue(userChats.size() > 0);

        HashMap<String, String> pairMap = userChats.get(0);

        assertEquals(Integer.toString(1), pairMap.get("ChatID"));

    }

    @Test
    void createdChatShouldHaveAssociatedCreatorPair() {
        connection = DatabasePool.getConnection();

        int userID = 5;

        ArrayList<HashMap<String, String>> userChats = connection.pullUserChats(userID);

        assertEquals(1, userChats.size());
    }

    @Test
    void deleteChatShouldReturnTrueAndDeleteChatPair() {

        connection = DatabasePool.getConnection();

        assertTrue(connection.deleteChat(createdChatOne));

        ArrayList<HashMap<String, String>> userChats = connection.pullUserChats(3);

        assertEquals(0, userChats.size());

        connection.close();
    }


    @Test
    void createMessageShouldReturnTrueAndCreateNewMessage()
    {
        connection = DatabasePool.getConnection();

        assertTrue(connection.addMessage(createdChatThree, 28, "TestSender", "This is a test message."));

        ArrayList<HashMap<String, String>> messageList = connection.pullMessagesFromChat(createdChatThree);

        assertTrue(messageList.size() > 0);

        System.out.println(messageList.get(0).get("Message"));
        connection.close();
    }


    @Test
    void deleteNonexistentMessageShouldReturnFalse()
    {
        connection = DatabasePool.getConnection();

        assertFalse(connection.deleteMessage(1000000));

        connection.close();
    }

}