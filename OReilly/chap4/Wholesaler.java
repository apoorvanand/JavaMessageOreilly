/*
 * Copyright (C) 2000, O'Reilly & Associates, Inc.
 * The code in this file may be copied, distributed, and reused,
 * provided that you do not remove this copyright notice.
 * O'Reilly & Associates assumes no responsibility for damages
 * resulting from the use of this code.
 */

package chap4.b2b;

import java.util.StringTokenizer;
import java.util.Properties;
import javax.naming.*;
import javax.jms.TopicConnectionFactory;
import javax.jms.Topic;
import javax.jms.Session;
import javax.jms.TextMessage;

public class Wholesaler implements javax.jms.MessageListener{

   private javax.jms.TopicConnection connect = null;
   private javax.jms.TopicSession pubSession = null;
   private javax.jms.TopicSession subSession = null;
   private javax.jms.TopicPublisher publisher = null;
   private javax.jms.TopicSubscriber subscriber = null;
   private javax.jms.Topic hotDealsTopic = null;
   private javax.jms.TemporaryTopic buyOrdersTopic = null;

   private boolean topicRequestor = false;
   private boolean requestReplyReceive = false;

   public Wholesaler(String broker, String username, String password){
      try{
        TopicConnectionFactory factory = null;
        InitialContext jndi = null;

        Properties env = new Properties();
        // ... specify the JNDI properties specific to the JNDI SPI being used
        env.put("BROKER", broker);
        jndi = new InitialContext(env);

        factory =
            (TopicConnectionFactory)jndi.lookup("TopicConnectionFactory");

        connect = factory.createTopicConnection (username, password);

        pubSession =
            connect.createTopicSession(false,Session.AUTO_ACKNOWLEDGE);
        subSession =
            connect.createTopicSession(false,Session.AUTO_ACKNOWLEDGE);

        hotDealsTopic = (Topic)jndi.lookup("Hot Deals");

        publisher = pubSession.createPublisher(hotDealsTopic);

        buyOrdersTopic = subSession.createTemporaryTopic();

        subscriber = subSession.createSubscriber(buyOrdersTopic);

        // The printed examples in Chapter 4 cover asynchronous pub/sub, synchronous request/reply using the
        // TopicRequestor object, and synchronous request/reply without the use of the TopicRequestor object.
        // This one example covers all three situations, and has the different functionality bracketed by
        // the use of the "topicRequestor" and "requestReply" booleans.
        // TopicRequestor and receive() do not require a message listener
        if(!topicRequestor && !requestReplyReceive)
            subscriber.setMessageListener(this);

        connect.start();

      }catch (javax.jms.JMSException jmse){
         jmse.printStackTrace(); System.exit(1);
      }catch(javax.naming.NamingException jne){
         jne.printStackTrace(); System.exit(1);
      }
   }
   private void publishPriceQuotes(String dealDesc, String username,
                                   String itemDesc,  float oldPrice,
                                   float newPrice){
      try{
        javax.jms.StreamMessage message = pubSession.createStreamMessage();
        message.writeString(dealDesc);
        message.writeString(itemDesc);
        message.writeFloat(oldPrice);
        message.writeFloat(newPrice);

        message.setStringProperty("Username", username);
        message.setStringProperty("Itemdesc", itemDesc);

        message.setJMSReplyTo(buyOrdersTopic);

        // The printed examples in Chapter 4 cover asynchronous pub/sub, synchronous request/reply using the
        // TopicRequestor object, and synchronous request/reply without the use of the TopicRequestor object.
        // This one example covers all three situations, and has the different functionality bracketed by
        // the use of the "topicRequestor" and "requestReply" booleans.
        if(topicRequestor)
        {
            //System.out.println("\nInitiating Synchronous Request");
            javax.jms.TopicRequestor requestor =
                new javax.jms.TopicRequestor(pubSession, hotDealsTopic);
            javax.jms.Message aMessage = requestor.request(message);
            if (aMessage == null)
                System.out.println("\nrequest() returned null");
            else
                System.out.println("\nRequest Sent, Reply Received!");
            if (aMessage != null)
            {
                onMessage(aMessage);
            }
            return;
        }

        // Publish the message.  If its an asynchronous pub/sub, the onMessage handler
        // will get called automatically
        // If its a synchronous request/reply via receive(), call onMessage directly
        publisher.publish(
            message,
            javax.jms.DeliveryMode.PERSISTENT,
            javax.jms.Message.DEFAULT_PRIORITY,
            1800000);

        if(requestReplyReceive)
        {
            javax.jms.Message aMessage = subscriber.receive();
            if (aMessage == null)
                System.out.println("\nreceive() returned null");
            else
                System.out.println("\nRequest Sent, Reply Received!");

            if (aMessage != null)
            {
                onMessage(aMessage);
            }
            return;
        }
      }catch ( javax.jms.JMSException jmse ){
         jmse.printStackTrace();
      }
   }
   public void onMessage( javax.jms.Message message){
      try{
         TextMessage textMessage = (TextMessage) message;
         String text = textMessage.getText();
         System.out.println("\nOrder received - "+text+
                            " from " + message.getJMSCorrelationID());
      }catch (java.lang.Exception rte){
         rte.printStackTrace();
      }
   }
   public void exit(){
      try{
        connect.close();
      }catch (javax.jms.JMSException jmse){
        jmse.printStackTrace();
      }
      System.exit(0);
   }
   public static void main(String argv[]) {
      String broker, username, password;
      if(argv.length == 3){
         broker = argv[0];
         username = argv[1];
         password = argv[2];
      }else{
         System.out.println("Invalid arguments. Should be: ");
         System.out.println("java Wholesaler broker username password");
         return;
      }

      Wholesaler wholesaler = new Wholesaler(broker, username, password);

      try{
         // Read all standard input and send it as a message.
         java.io.BufferedReader stdin = new java.io.BufferedReader
            (new java.io.InputStreamReader( System.in ) );
         System.out.println ("Enter: Item, Old Price, New Price ");
         System.out.println("\ne.g. Bowling Shoes, 100.00, 55.00");

         while ( true ){
            String dealDesc = stdin.readLine();
            if(dealDesc != null && dealDesc.length() > 0){
               // parse the deal description
               StringTokenizer tokenizer =
               new StringTokenizer(dealDesc,",") ;
                  String itemDesc = tokenizer.nextToken();
                  String temp = tokenizer.nextToken();
                  float oldPrice =
                    Float.valueOf(temp.trim()).floatValue();
                  temp = tokenizer.nextToken();
                  float newPrice =
                    Float.valueOf(temp.trim()).floatValue();

               wholesaler.publishPriceQuotes(dealDesc,username,
                                             itemDesc, oldPrice,newPrice);
            }else{
                wholesaler.exit();
            }
         }
      }catch( java.io.IOException ioe ){
         ioe.printStackTrace();
      }
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