#This script initiates the server.

ls var/server
chmod +x /var/server/gradlew
./var/server/gradlew build
java var/server/bin/messengerserver/Server