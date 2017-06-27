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
        connection.setClientID( username );

        // Create a JMS session object
        TopicSession pubSession =
            connection.createTopicSession(false,
                                      Session.AUTO_ACKNOWLEDGE);
        TopicSession subSession =
            connection.createTopicSession(false,
                                      Session.AUTO_ACKNOWLEDGE);

        // Lookup a JMS topic
        Topic chatTopic = null;
        try {        
            chatTopic = (Topic)jndi.lookup(topicName);
        } catch ( ClassCastException e ) {
            System.err.println( "The topic name \"" + topicName + 
                                "\" causes a ClassCastException :" );
            e.printStackTrace();
            System.err.println( "Do not use \"QueueConnectionFactory\" and " +
                                "\"TopicConnectionFactory\" as topic name." );
            System.exit( -1 );
        }

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
            String username = "guest";
            String password = "guest";

            if ( args.length < 1 || args.length > 3 ) {
                System.out.println("Topic or username missing"+args.length);
                System.exit( -1 );
            }

            if ( args.length == 3 ) {
                username = args[1];
                password = args[2];
            }

            // args[0]=topicName; args[1]=username; args[2]=password
            Chat chat = new Chat(args[0],username,password);

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
                return (new ch.softwired.jms.IBusTopic(str) );
            else if ( str.equalsIgnoreCase("Buy Order") )
                return (new ch.softwired.jms.IBusTopic(str) );
            else if ( str.equalsIgnoreCase("Reply Q") )
                return (new ch.softwired.jms.IBusQueue("SampleQ1") );
            else if( str.equalsIgnoreCase("TopicConnectionFactory") )
                return (new ch.softwired.jms.IBusTopicConnectionFactory() );
            else if( str.equalsIgnoreCase("QueueConnectionFactory") )
                return (new ch.softwired.jms.IBusQueueConnectionFactory() );
            else // its whatever "Chat" topic was passed in on the command line
                return (new ch.softwired.jms.IBusTopic(str) );      
        }
    }
}
