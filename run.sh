#!/bin/sh

mvn clean compile
tmux split-window -v mvn exec:java -Dexec.mainClass="org.example.ServerMain" -Dexec.args="2"
tmux split-window -h mvn exec:java -Dexec.mainClass="org.example.ServerMain" -Dexec.args="3"
tmux split-window -h mvn exec:java -Dexec.mainClass="org.example.ServerMain" -Dexec.args="4"
tmux new-window mvn exec:java -Dexec.mainClass="org.example.ClientMain" -Dexec.args="7011"
tmux new-window mvn exec:java -Dexec.mainClass="org.example.ClientMain" -Dexec.args="7012"
mvn exec:java -Dexec.mainClass="org.example.ServerMain" -Dexec.args="1"
