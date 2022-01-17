#This script initiates the server.

wget https://services.gradle.org/distributions/gradle-5.0-bin.zip -P /tmp
sudo unzip -d /opt/gradle /tmp/gradle-5.0-bin.zip



cd var/server
gradle -v
#java var/server/bin/messengerserver/Server

java bin/messengerserver/Server