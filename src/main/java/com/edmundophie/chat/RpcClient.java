package com.edmundophie.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import com.rabbitmq.client.Channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by edmundophie on 10/9/15.
 */
public class RpcClient {
    private static final String HOSTNAME = "localhost";
    private static final String ENCODING = "UTF-8";
    private static final String MESSAGE_EXCHANGE_NAME = "messages";

    private Connection connection;
    private Channel channel;
    private Channel messageInChannel;
    private String requestQueueName = "rpc_queue";
    private String replyQueueName;
    private QueueingConsumer consumer;
    private Consumer messageInConsumer;

    private ObjectMapper mapper;
    private boolean isLoggedIn;
    private String nickname;

    public RpcClient() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOSTNAME);
        factory.setConnectionTimeout(0);
        Connection connection = factory.newConnection();
        channel = connection.createChannel();

        replyQueueName = channel.queueDeclare().getQueue();
        consumer = new QueueingConsumer(channel);
        channel.basicConsume(replyQueueName, true, consumer);

        messageInChannel = connection.createChannel();
        messageInChannel.exchangeDeclare(MESSAGE_EXCHANGE_NAME, "direct");

        messageInConsumer = new DefaultConsumer(messageInChannel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope,
                                       AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println(message);
            }
        };

        mapper = new ObjectMapper();
        isLoggedIn = false;
        nickname = "";
    }

    public String call(String message) throws Exception {
        String response = null;
        String corrId = UUID.randomUUID().toString();

        AMQP.BasicProperties props = new AMQP.BasicProperties
                .Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();
        channel.basicPublish("", requestQueueName, props, message.getBytes(ENCODING));

        while (true) {
            QueueingConsumer.Delivery delivery = consumer.nextDelivery();
            if(delivery.getProperties().getCorrelationId().equals(corrId)) {
                response = new String(delivery.getBody(), ENCODING);
                break;
            }
        }

        return response;
    }

    public void close() throws Exception {
        channel.close();
        connection.close();
    }

    public static void main(String[] args) {
        System.out.println("* Starting client...");

        RpcClient rpcClient = null;
        String response = null;

        try {
            rpcClient = new RpcClient();

            rpcClient.perform();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(rpcClient!=null) {
                try{
                    rpcClient.close();
                } catch (Exception ignore) {}
            }
        }
        System.exit(0);
    }

    private static void printInvalidCommand() {
        System.err.println("* Invalid Command");
    }

    private void perform() throws Exception {
        System.out.println("* Client started");

        String command = null;
        do {
//            String consoleNickname = (!isLoggedIn)?"$ ":nickname+"$ ";
//            System.out.print(consoleNickname);
            String input = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

            if(input.isEmpty())
                printInvalidCommand();
            else {
                String parameter = "";
                int i = input.indexOf(" ");

                if (i > -1) {
                    command = input.substring(0, i);
                    parameter = input.substring(i + 1);
                } else
                    command = input;

                if (command.equalsIgnoreCase("NICK")) {
                    login(command.toUpperCase(), parameter);
                } else if (command.equalsIgnoreCase("JOIN")) {
                    join(command.toUpperCase(), parameter);
                } else if (command.equalsIgnoreCase("LEAVE")) {
                    leave(command.toUpperCase(), parameter);
                } else if (command.equalsIgnoreCase("LOGOUT")) {
                    logout(command.toUpperCase());
                } else if (command.equalsIgnoreCase("EXIT")) {
                    exit(command.toUpperCase());
                } else if (command.charAt(0) == '@') {
                    sendMessage(command.substring(1), parameter);
                } else {
                    broadcastMessage(input);
                }
            }
        } while (!command.equalsIgnoreCase("EXIT"));
    }

    private void login(String command, String parameter) throws Exception {
        if(isLoggedIn) {
            System.err.println("* Please logout first!");
        } else {
            Request request = new Request();
            request.setCommand(command);
            request.setNickname(parameter);

            String responseJson = call(request.toString());
            Response response = mapper.readValue(responseJson, Response.class);

            if(response.isStatus()) {
                nickname = response.getNickname();
                isLoggedIn = true;
                System.out.println(response.getMessage());
            } else {
                System.err.println(response.getMessage());
            }
        }
    }

    private void join(String command, String parameter) throws Exception {
        if(!isLoggedIn) System.err.println("* Please login first!");
        else if(parameter==null || parameter.isEmpty()) printInvalidCommand();
        else {
            Request request = new Request();
            request.setCommand(command);
            request.setChannelName(parameter);
            request.setNickname(nickname);

            String responseJson = call(request.toString());
            Response response = mapper.readValue(responseJson, Response.class);

            if(response.isStatus()) {
                messageInChannel.queueDeclare(nickname, false, false, true, null);
                messageInChannel.queueBind(nickname, MESSAGE_EXCHANGE_NAME, nickname);
                messageInChannel.basicConsume(nickname, true, messageInConsumer);
                System.out.println(response.getMessage());
            } else
                System.err.println(response.getMessage());
        }
    }

    private void leave(String command, String parameter) throws Exception {
        if(!isLoggedIn) System.err.println("* Please login first!");
        else if(parameter==null || parameter.isEmpty()) printInvalidCommand();
        else  {
            Request request = new Request();
            request.setCommand(command);
            request.setChannelName(parameter);
            request.setNickname(nickname);

            String responseJson = call(request.toString());
            Response response = mapper.readValue(responseJson, Response.class);

            if(response.isStatus()) {
                System.out.println(response.getMessage());
            } else
                System.err.println(response.getMessage());
        }
    }

    private void logout(String command) throws Exception {
        if(!isLoggedIn) System.err.println("* Please login first!");
        else {
            Request request = new Request();
            request.setCommand(command);
            request.setNickname(nickname);

            String responseJson = call(request.toString());
            Response response = mapper.readValue(responseJson, Response.class);

            if(response.isStatus()) {
                messageInChannel.queueUnbind(nickname, MESSAGE_EXCHANGE_NAME, nickname);
                messageInChannel.queueDelete(nickname);
                isLoggedIn = false;
                nickname = "";
                System.out.println(response.getMessage());
            } else {
                System.err.println(response.getMessage());
            }
        }
    }

    private void exit(String command) throws Exception {
        if(isLoggedIn)
            logout(command);

        System.out.println("* Program exited");
    }


    private void sendMessage(String channelName, String message) throws Exception {
        if(!isLoggedIn) System.err.println("* Please login first!");
        else if(message==null || message.isEmpty()) printInvalidCommand();
        else {
            Request request = new Request();
            request.setCommand("SEND");
            request.setChannelName(channelName);
            request.setMessage(message);
            request.setNickname(nickname);

            String responseJson = call(request.toString());
            Response response = mapper.readValue(responseJson, Response.class);

            if(!response.isStatus()) {
                System.err.println(response.getMessage());
            }
        }
    }

    private void broadcastMessage(String message) throws Exception {
        if(!isLoggedIn) System.err.println("Please login first!");
        else {
            Request request = new Request();
            request.setCommand("BROADCAST");
            request.setMessage(message);
            request.setNickname(nickname);

            String responseJson = call(request.toString());
            Response response = mapper.readValue(responseJson, Response.class);

            if(!response.isStatus()) {
                System.err.println(response.getMessage());
            }
        }
    }

}
