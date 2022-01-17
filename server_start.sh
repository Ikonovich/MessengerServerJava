#!/bin/sh

export GRADLE_HOME=/opt/gradle/gradle-5.0
export PATH=${GRADLE_HOME}/bin:${PATH}

cd var/server
gradle build
#java var/server/bin/messengerserver/Server

java bin/messengerserver/Server