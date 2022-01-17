#!/bin/sh

export GRADLE_HOME=/opt/gradle/gradle-5.0
export PATH=${GRADLE_HOME}/bin:${PATH}

export PATH=${JAVA_HOME}/lib:${PATH}

cd var/server
gradle build

sudo systemctl daemon-reload
sudo systemctl enable your-application.service
sudo systemctl start your-application
sudo systemctl status your-application -l