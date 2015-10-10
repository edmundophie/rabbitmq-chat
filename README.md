# rabbitmq-chat
CLI Chat Program Based on RabbitMQ

## Requirements
 - JRE >= 1.7
 - [Maven](https://maven.apache.org/download.cgi) installed
 - [RabbitMQ 3.5.6 server](http://www.rabbitmq.com/download.html) installed


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

## Testing
![alt text](https://github.com/edmundophie/rabbiymq-chat/blob/master/blob/coming_soon.png "Screenshot tes 1")

## Team Member
- Edmund Ophie 13512095
- Kevin 13512097

## [Github Link](https://github.com/edmundophie/rabbitmq-chat.git) 
