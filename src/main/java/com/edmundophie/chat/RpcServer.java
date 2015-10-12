package com.edmundophie.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;
import java.util.*;

/**
 * Created by edmundophie on 10/9/15.
 */
public class RpcServer {
    private static final String RPC_QUEUE_NAME = "rpc_queue";
    private static final String HOSTNAME = "localhost";
    private static final String ENCODING = "UTF-8";
    private static final String MESSAGE_EXCHANGE_NAME = "messages";
    private static final int MAX_GENERATED_RANDOM_ACCOUNT_INT = 99999;
    private static Map<String, User> userMap;
    private static Map<String, Channel> channelMap;
    private static Map<String, List<Message>> messageListMap; // TODO HIGH change to publish subscribe
    private static com.rabbitmq.client.Channel messageOutChannel;
    private static Map<String, List<String>> channelMemberMap;

    public static void main (String[] args) {
        Connection connection = null;
        com.rabbitmq.client.Channel channel = null;

        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(HOSTNAME);

            connection = factory.newConnection();
            channel = connection.createChannel();

            channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null);
            channel.queuePurge(RPC_QUEUE_NAME);
            channel.basicQos(1);

            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(RPC_QUEUE_NAME, false, consumer);

            messageOutChannel = connection.createChannel();
            messageOutChannel.exchangeDeclare(MESSAGE_EXCHANGE_NAME, "direct");

            userMap =  new HashMap<String, User>();
            channelMap =  new HashMap<String, Channel>();
            messageListMap = new HashMap<String, List<Message>>();
            channelMemberMap= new HashMap<String, List<String>>();

            System.out.println("- RPC server started");

            while (true) {
                String response = null;

                QueueingConsumer.Delivery delivery = consumer.nextDelivery();

                AMQP.BasicProperties props = delivery.getProperties();
                AMQP.BasicProperties replyProps = new AMQP.BasicProperties
                        .Builder()
                        .correlationId(props.getCorrelationId())
                        .build();

                String message = new String(delivery.getBody(), ENCODING);

                response = "" + processMessage(message);

                channel.basicPublish("", props.getReplyTo(), replyProps, response.getBytes(ENCODING));
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String processMessage(String message) throws JsonProcessingException {
        Request request = null;
        try {
            request = new ObjectMapper().readValue(message, Request.class);
        } catch (IOException e) {
            Response response = new Response();
            response.putStatus(false);
            response.setMessage("* Server Encountered An Error On Processing Message!");
            return new ObjectMapper().writeValueAsString(response);
        }

        if (request.getCommand().equalsIgnoreCase("NICK")) {
            return login(request.getNickname());
        } else if (request.getCommand().equalsIgnoreCase("JOIN")) {
            return join(request.getNickname(), request.getChannelName());
        } else if (request.getCommand().equalsIgnoreCase("LEAVE")) {
            return leave(request.getNickname(), request.getChannelName());
        } else if (request.getCommand().equalsIgnoreCase("LOGOUT")) {
            return logout(request.getNickname());
        } else if (request.getCommand().equalsIgnoreCase("EXIT")) {
            return exit(request.getNickname());
        } else if (request.getCommand().equalsIgnoreCase("SEND")) {
            return sendMessage(request.getNickname(), request.getChannelName(), request.getMessage());
        } else if (request.getCommand().equalsIgnoreCase("BROADCAST")) {
            return broadcastMessage(request.getNickname(), request.getMessage());
        } else {
            Response response = new Response();
            response.putStatus(false);
            response.setMessage("* Unknown Message Command!");
            return new ObjectMapper().writeValueAsString(response);
        }
    }

    private static String login(String nickname) {
        System.out.println("- Login method invoked");
        StringBuilder message = new StringBuilder();

        if(nickname==null || nickname.isEmpty() || userMap.containsKey(nickname)) {
            if(userMap.containsKey(nickname)) message.append("* Username exist!\n");
            nickname = generateRandomNickname();
            message.append("* Random user generated\n");
        }
        message.append("* Successfully logged in as " + nickname);

        userMap.put(nickname, new User(nickname));

        Response response = new Response(true, message.toString(), nickname);
        return response.toString();
    }

    private static String generateRandomNickname() {
        String newNickname;
        Random random = new Random();
        do {
            newNickname = "user" + random.nextInt(MAX_GENERATED_RANDOM_ACCOUNT_INT);
        } while(userMap.containsKey(newNickname));

        return newNickname;
    }

    public static String join(String nickname, String channelName) {
        System.out.println("- " + nickname + " requested to join #" + channelName);

        List userChannelList = userMap.get(nickname).getJoinedChannel();
        StringBuilder message = new StringBuilder();
        Response response = new Response();

        if(userChannelList.contains(channelName)) {
            message.append("* You are already a member of #" + channelName);
            response.putStatus(false);
        } else {
            if(!channelMap.containsKey(channelName)) {
                channelMap.put(channelName, new Channel(channelName));
                messageListMap.put(channelName, new ArrayList<Message>());
                channelMemberMap.put(channelName, new ArrayList<String>());
                message.append("* Created new channel #" + channelName + "\n");
            }

            userChannelList.add(channelName);
            channelMemberMap.get(channelName).add(nickname);
            message.append("* #" + channelName + " joined successfully");
            response.putStatus(true);
        }

        response.setMessage(message.toString());
        return response.toString();
    }

    public static String leave(String nickname, String channelName) {
        System.out.println("- " + nickname + " request to leave #" + channelName);

        StringBuilder message = new StringBuilder();
        Response response = new Response();

        if(!userMap.get(nickname).getJoinedChannel().contains(channelName)) {
            System.err.println("- Failed to leave channel. " + nickname + " is not a member of #" + channelName);
            message.append("* Failed to leave.\n* You are not a member of #" + channelName);
            response.putStatus(false);
        } else {
            userMap.get(nickname).getJoinedChannel().remove(channelName);
            channelMemberMap.get(channelName).remove(nickname);
            response.putStatus(true);
            message.append("* You are no longer a member of #" + channelName);
        }

        response.setMessage(message.toString());
        return response.toString();
    }

    public static String logout(String nickname) {
        System.out.println("- " + nickname + " requested to logout");
        userMap.remove(nickname);

        Response response = new Response();
        response.putStatus(true);
        response.setMessage("* " + nickname + " have been logged out");

        return response.toString();
    }

    public static String exit(String nickname) {
        return logout(nickname);
    }

    public static String sendMessage(String nickname, String channelName, String message) {
        System.out.println("- " + nickname + " sends a message to #" + channelName);
        StringBuilder returnedMessage = new StringBuilder();
        Response response = new Response();

        List<String> userChannelList = userMap.get(nickname).getJoinedChannel();
        if(!userChannelList.contains(channelName)) {
            System.err.println("- Failed to send " + nickname + " message to #" + channelName + ". User is not a member of the channel.");
            returnedMessage.append("* You are not a member of #" + channelName);
            response.putStatus(false);
        } else {
            try {
                Message msg = new Message(nickname, message);
                distributeMessage(msg, channelName);
            } catch (IOException e) {
                e.printStackTrace();
                response.putStatus(false);
                response.setMessage("* Server Encountered An Error On Publishing the Message");
                return response.toString();
            }
            response.putStatus(true);
        }

        response.setMessage(returnedMessage.toString());
        return response.toString();
    }

    public static String broadcastMessage(String nickname, String message) {
        System.out.println("- " + nickname + " broadcasts a message");
        StringBuilder returnedMessage = new StringBuilder();
        Response response = new Response();

        List<String> userChannelList = userMap.get(nickname).getJoinedChannel();
        if(userChannelList.size()==0) {
            System.err.println("- Failed to send " + nickname + " message. No channel found.");
            returnedMessage.append("* Failed to send the message\n* You haven't join any channel yet");
            response.putStatus(false);
        } else {
            try {
                Message msg = new Message(nickname, message);
                distributeMessage(msg, userChannelList);
            } catch (IOException e) {
                e.printStackTrace();
                response.putStatus(false);
                response.setMessage("* Server Encountered An Error On Publishing the Message");
                return response.toString();
            }
            response.putStatus(true);
        }

        response.setMessage(returnedMessage.toString());
        return response.toString();
    }

    public static void distributeMessage(Message message, List<String> userChannelList) throws IOException {
        for(String channelName:userChannelList) {
            String enrichedMessage = "@" + channelName + " " + message.getSender() + ": " + message.getText();
            for(String routingKey:channelMemberMap.get(channelName)) {
                if(userMap.containsKey(routingKey))
                    messageOutChannel.basicPublish(MESSAGE_EXCHANGE_NAME, routingKey, null, enrichedMessage.getBytes());
            }
        }
    }

    public static void distributeMessage(Message message, String channelName) throws IOException {
        String enrichedMessage = "@" + channelName + " " + message.getSender()+ ": " + message.getText();
        for(String routingKey:channelMemberMap.get(channelName)) {
            if(userMap.containsKey(routingKey))
                messageOutChannel.basicPublish(MESSAGE_EXCHANGE_NAME, routingKey, null, enrichedMessage.getBytes());
        }
    }
}
