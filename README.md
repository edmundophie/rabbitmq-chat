# rabbitmq-chat
CLI Chat Program Based on RabbitMQ

## Requirements
 - JRE >= 1.7
 - [Maven](https://maven.apache.org/download.cgi) installed
 - [RabbitMQ 3.5.6 server](http://www.rabbitmq.com/download.html) installed on localhost


## How to Build
1. Resolve maven dependency  

	 ```
	 $ mvn dependency:copy-dependencies
	 ```
2. Build `jar` using maven `mvn`  

	 ```
	 $ mvn package
	 ```

## How to Run	 
1. Run `RpcServer` from the generated `jar` in `target` folder  

	 ```
	 $ java -cp target/dependency/*:target/rabbitmq-chat-1.0.jar com.edmundophie.chat.RpcServer
	 ```
2. Run `RpcClient` from the generated `jar` in `target` folder  

	 ```
	 $ java -cp target/dependency/*:target/rabbitmq-chat-1.0.jar com.edmundophie.chat.RpcClient
	 ```

## Chat Commands
- `nick <nickname>` : login as `nickname`. Leave `nickname` empty to login as a random user
- `join <channelname>` : join to a channel named `channelname`
- `leave <channelname>` : leave a channel named `channelname`
- `@<channelname> <message>` :  send `message` to a channel named `channelname`
- `<message>` : send a message to all user joined channel
- `logout` : logout from current `nickname`
- `exit` : stop program

## Testing
Conducted Testing:
* All commands (nick, join, leave, etc)
* Send a message to a channel that is not a member of
* Join a channel that already a member of
* Leave a channel that is not a member of
* Multiclient chat

![alt text](https://github.com/edmundophie/rabbiymq-chat/blob/master/blob/coming_soon.png "Screenshot tes 1")

## Team Member
- Edmund Ophie 13512095
- Kevin 13512097

## [Github Link](https://github.com/edmundophie/rabbitmq-chat.git) 
