/*
 * Copyright (C) 2000, O'Reilly & Associates, Inc.
 * The code in this file may be copied, distributed, and reused,
 * provided that you do not remove this copyright notice.
 * O'Reilly & Associates assumes no responsibility for damages
 * resulting from the use of this code.
 */

package chap6.b2b;

import java.util.StringTokenizer;
import java.util.Properties;
import javax.naming.*;
import javax.jms.TopicConnectionFactory;
import javax.jms.Topic;
import javax.jms.Session;
import javax.jms.TextMessage;

public class Wholesaler implements
    javax.jms.MessageListener,
    javax.jms.ExceptionListener
{

   private javax.jms.TopicConnection connect = null;
   private javax.jms.TopicSession session = null;
   private javax.jms.TopicPublisher publisher = null;
   private javax.jms.TopicSubscriber subscriber = null;
   private javax.jms.Topic hotDealsTopic = null;
//   private javax.jms.TemporaryTopic buyOrdersTopic = null;
   private javax.jms.Topic buyOrdersTopic = null;
   private String mBroker = null;
   private String mUsername = null;
   private String mPassword = null;
   private static final int    CONNECTION_RETRY_PERIOD = 10000;  // milliseconds (10 sec)
   private static boolean inSequence = false;

   public Wholesaler(String broker, String username, String password){
        mBroker = broker;
        mUsername = username;
        mPassword = password;

        establishConnection(broker, username, password);
   }
   private void establishConnection(String broker, String username, String password)
   {
      try{
        TopicConnectionFactory factory = null;
        InitialContext jndi = null;

        Properties env = new Properties();
        // ... specify the JNDI properties specific to the JNDI SPI being used
        env.put("BROKER", broker);
        jndi = new InitialContext(env);
        factory =
            (TopicConnectionFactory)jndi.lookup("TopicConnectionFactory");

        while (connect == null)
        {
            try{
                connect = factory.createTopicConnection (username, password);
                // SonicMQ vendor specific extension.
                // Ping the broker periodically to ensure that the connection
                // is still active.
                ((progress.message.jclient.TopicConnection)connect).setPingInterval(30);
            } catch (javax.jms.JMSException jmse)
            {
                System.out.print("Cannot connect to message server: " + broker + "...");
                System.out.println("Pausing " +
                    CONNECTION_RETRY_PERIOD / 1000 + " seconds before retry.");
                try
                {
                    Thread.sleep(CONNECTION_RETRY_PERIOD);
                } catch (java.lang.InterruptedException ie) {ie.printStackTrace();}
                continue;
            }
        }
        System.out.println("\nConnection established");
        session =
        connect.createTopicSession(false,Session.AUTO_ACKNOWLEDGE);

        hotDealsTopic = (Topic)jndi.lookup("Hot Deals");
        buyOrdersTopic = (Topic)jndi.lookup("Buy Order");

        publisher = session.createPublisher(hotDealsTopic);

        //buyOrdersTopic = session.createTemporaryTopic();

        subscriber = session.createSubscriber(buyOrdersTopic);
        subscriber.setMessageListener(this);

        connect.setExceptionListener( (javax.jms.ExceptionListener) this);
        connect.start();

      }catch (javax.jms.JMSException jmse){
         jmse.printStackTrace(); System.exit(1);
      }catch(javax.naming.NamingException jne){
         jne.printStackTrace(); System.exit(1);
      }
   }
   private void publishPriceQuotes(String dealDesc, String username,
                                   String itemdesc,  float oldprice,
                                   float newprice){
      try{
        javax.jms.StreamMessage message = session.createStreamMessage();
        message.writeString(dealDesc);
        message.writeString(itemdesc);
        message.writeFloat(oldprice);
        message.writeFloat(newprice);

        message.setStringProperty("Username", username);
        message.setStringProperty("Itemdesc", itemdesc);

        message.setJMSReplyTo(buyOrdersTopic);

        publisher.publish(
            message,
            javax.jms.DeliveryMode.PERSISTENT,
            javax.jms.Message.DEFAULT_PRIORITY,
            1800000);

      }catch ( javax.jms.JMSException jmse ){
         jmse.printStackTrace();
      }
   }
   private void sendSequenceMarker(String sequenceMarker){
      try{
        javax.jms.StreamMessage message = session.createStreamMessage();
        message.setStringProperty("SEQUENCE_MARKER",sequenceMarker);

        publisher.publish(
            message,
            javax.jms.DeliveryMode.PERSISTENT,
            javax.jms.Message.DEFAULT_PRIORITY,
            1800000);

      }catch ( javax.jms.JMSException jmse ){
         jmse.printStackTrace();
      }
   }

   public void onMessage( javax.jms.Message message){
      try{
         TextMessage textMessage = (TextMessage) message;
         String text = textMessage.getText();
         System.out.println("Order received - "+text+
                            " from " + message.getJMSCorrelationID());
      }catch (java.lang.Exception rte){
         rte.printStackTrace();
      }
   }
   public void onException ( javax.jms.JMSException jsme)
   {
        // See if connection was dropped.

        // Tell the user that there is a problem.
        System.err.println ("\n\nThere is a problem with the connection.");
        System.err.println ("   JMSException: " + jsme.getMessage());

        // See if the error is a dropped connection. If so, try to reconnect.
        // NOTE: the test is against Progress SonicMQ error codes.
        int dropCode = progress.message.jclient.ErrorCodes.ERR_CONNECTION_DROPPED;
        if (progress.message.jclient.ErrorCodes.testException(jsme, dropCode))
        {
            System.err.println ("Please wait while the application tries to "+
                                "re-establish the connection...");
            // Reestablish the connection
            connect = null;
            establishConnection(mBroker, mUsername, mPassword);
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
            if( dealDesc != null && dealDesc.length() > 0 ){
                if( dealDesc.substring(0,3).equalsIgnoreCase("END") ){
                    wholesaler.sendSequenceMarker( "END_SEQUENCE" );
                }else{
                    // parse the deal description
                    StringTokenizer tokenizer =
                    new StringTokenizer(dealDesc,",") ;
                    String itemdesc = tokenizer.nextToken();
                    String temp = tokenizer.nextToken();
                    float oldprice =
                        Float.valueOf(temp.trim()).floatValue();
                    temp = tokenizer.nextToken();
                    float newprice =
                        Float.valueOf(temp.trim()).floatValue();

                    wholesaler.publishPriceQuotes(dealDesc,username,
                                                    itemdesc, oldprice,newprice);
                }
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