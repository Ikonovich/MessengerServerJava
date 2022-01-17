#!/bin/sh

export GRADLE_HOME=/opt/gradle/gradle-5.0
export PATH=${GRADLE_HOME}/bin:${PATH}

export PATH=${JAVA_HOME}/lib:${PATH}

cd var/server
gradle build
java build/classes/java/main/messengerserver/Server

#java bin/messengerserver/Server