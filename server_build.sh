#!/bin/sh

export GRADLE_HOME=/opt/gradle/gradle-5.0
export PATH=${GRADLE_HOME}/bin:${PATH}

export PATH=${JAVA_HOME}/lib:${PATH}

export CLASSPATH=$CLASSPATH:/var/server/mysql-connector-java-8.0.27.jar
export CLASSPATH=$CLASSPATH:/var/server/gson-2.8.2.jar

cd var/server
gradle build

sudo systemctl daemon-reload
sudo systemctl enable MessengerServer
sudo systemctl start MessengerServer
sudo systemctl status MessengerServer -l