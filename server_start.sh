#This script initiates the server.

ls var/server
sudo ./var/server/gradlew build
java var/server/bin/messengerserver/Server