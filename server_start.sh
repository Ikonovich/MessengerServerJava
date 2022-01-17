#This script initiates the server.

ls var/server
./var/server/gradlew build
java var/server/bin/messengerserver/Server