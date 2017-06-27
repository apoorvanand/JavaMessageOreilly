/*
 * Copyright (C) 2000, O'Reilly & Associates, Inc.
 * The code in this file may be copied, distributed, and reused,
 * provided that you do not remove this copyright notice.
 * O'Reilly & Associates assumes no responsibility for damages
 * resulting from the use of this code.
 */

package chap2.chat;

import javax.jms.*;
import javax.naming.*;
import java.io.*;
import java.io.InputStreamReader;
import java.util.Properties;

public class Chat implements javax.jms.MessageListener{
    private TopicSession pubSession;
    private TopicSession subSession;
    private TopicPublisher publisher;
    private TopicConnection connection;
    private String userName;

    /* Constructor. Establish JMS publisher and subscriber */
    public Chat(String topicName, String username, String password)
      throws Exception {

        // Obtain a JNDI connection
        Properties env = new Properties();
        // ... specify the JNDI properties specific to the vendor
        env.put("BROKER", "localhost");

        InitialContext jndi = new InitialContext(env);

        // Lookup a JMS connection factory
        TopicConnectionFactory conFactory =
            (TopicConnectionFactory)jndi.lookup("TopicConnectionFactory");

        // Create a JMS connection
        TopicConnection connection =
            conFactory.createTopicConnection(username,password);

        // Create a JMS session object
        TopicSession pubSession =
            connection.createTopicSession(false,
                                      Session.AUTO_ACKNOWLEDGE);
        TopicSession subSession =
            connection.createTopicSession(false,
                                      Session.AUTO_ACKNOWLEDGE);

        // Lookup a JMS topic
        Topic chatTopic = (Topic)jndi.lookup(topicName);

        // Create a JMS publisher and subscriber
        TopicPublisher publisher = 
            pubSession.createPublisher(chatTopic);
        TopicSubscriber subscriber = 
            subSession.createSubscriber(chatTopic);

        // Set a JMS message listener
        subscriber.setMessageListener(this);

        // Intialize the Chat application
        set(connection, pubSession, subSession, publisher, username);

        // Start the JMS connection; allows messages to be delivered
        connection.start();

    }
    /* Initializes the instance variables */
    public void set(TopicConnection con, TopicSession pubSess,
                    TopicSession subSess, TopicPublisher pub, 
                    String username) {
        this.connection = con;
        this.pubSession = pubSess;
        this.subSession = subSess;
        this.publisher = pub;
        this.userName = username;
    }
    /* Receive message from topic subscriber */
    public void onMessage(Message message) {
        try {
            TextMessage textMessage = (TextMessage) message;
            String text = textMessage.getText();
            System.out.println(text);
        } catch(JMSException jmse){ jmse.printStackTrace(); }
    }
    /* Create and send message using topic publisher */
    protected void writeMessage(String text)throws JMSException {
        TextMessage message = pubSession.createTextMessage();
        message.setText(userName+" : "+text);
        publisher.publish(message);
    }
    /* Close the JMS connection */
    public void close() throws JMSException {
        connection.close();
    }
    /* Run the Chat client */
    public static void main(String [] args){
        try{
            if(args.length!=3)
                System.out.println("Topic or username missing");

            // args[0]=topicName; args[1]=username; args[2]=password
            Chat chat = new Chat(args[0],args[1],args[2]);

            // read from command line
            BufferedReader commandLine = new 
              java.io.BufferedReader(new 
                                     InputStreamReader(System.in));

            // loop until the word "exit" is typed
            while(true){
                String s = commandLine.readLine();
                if(s.equalsIgnoreCase("exit")){
                    chat.close(); // close down connection
                    System.exit(0);// exit program
                }else
                    chat.writeMessage(s);
            }
        }catch(Exception e){ e.printStackTrace(); }
    }
}

class InitialContext
{
    String mBroker = null;
    
    public InitialContext (Properties env)
    {
        mBroker = env.getProperty("BROKER");
        return;
    }

    public Object lookup(String str) throws javax.naming.NamingException,
        javax.jms.JMSException
    {
        if ( str.equalsIgnoreCase("Hot Deals") )
            return (new progress.message.jclient.Topic(str));
        else if ( str.equalsIgnoreCase("Buy Order") )
            return (new progress.message.jclient.Topic(str));
        else if ( str.equalsIgnoreCase("Reply Q") )
            return (new progress.message.jclient.Queue("SampleQ1"));
        else if( str.equalsIgnoreCase("TopicConnectionFactory") )
            return (new progress.message.jclient.TopicConnectionFactory (mBroker));
        else if( str.equalsIgnoreCase("QueueConnectionFactory") )
            return (new progress.message.jclient.QueueConnectionFactory (mBroker));
        else // its whatever "Chat" topic was passed in on the command line
            return (new progress.message.jclient.Topic(str));        
    }
}