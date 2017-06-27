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
            
        // Close an already open connection in both the client's JMS runtime
        // and on the MessageServer itself, if possible (otherwise, warnings will
        // notify the client that the MessageServer has died).
        if ( connect != null ) {
            connect.close();
            connect = null;
        }

        while (connect == null)
        {            
            try{
                connect = factory.createTopicConnection (username, password);
                connect.setClientID( username );           
            } catch (javax.jms.JMSException jmse)
            {    
                jmse.printStackTrace();
                System.out.println( "TRY TO CONN FAILES" );
                System.out.println( "" + jmse.getLinkedException() );

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
   public void onException ( javax.jms.JMSException jmse )
   {
       System.err.println( "JMSException: " + jmse );
       // i.e. ch.softwired.jms.DisconnectedException,
       //      ch.softwired.jms.ReconnectException, 
       //      ch.softwired.jms.ServerDiedException

       if ( "ch.softwired.jms.ServerDiedException".equals
            ( jmse.getClass().getName() ) ) {
           System.err.println( "\nServer has died, client still trying ..." );
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
                 if( "END".equalsIgnoreCase( dealDesc ) ) {
                     wholesaler.sendSequenceMarker( "END_SEQUENCE" );
                 }else{ 
                     // parse the deal description 
                     String itemDesc = null;
                     String temp = null;
                     float oldPrice = 0;
                     float newPrice = 0;
                     try {
                         StringTokenizer tokenizer = 
                             new StringTokenizer(dealDesc,",") ;
                         itemDesc = tokenizer.nextToken();
                         temp = tokenizer.nextToken();
                         oldPrice = 
                             Float.valueOf(temp.trim()).floatValue();
                         temp = tokenizer.nextToken();
                         newPrice = 
                             Float.valueOf(temp.trim()).floatValue();                     
                     } catch ( java.util.NoSuchElementException e ) {
                         System.err.println( "Cannot parse deal descriptor " + 
                                             dealDesc );
                         continue;
                     } catch ( NumberFormatException e ) {
                         System.err.println( "Cannot parse deal descriptor " + 
                                         dealDesc );
                         continue;
                     } 

                     wholesaler.publishPriceQuotes(dealDesc,username,
                                                       itemDesc, oldPrice,newPrice);
                 }

             }else{
                 wholesaler.exit();
             }


         }
      }catch( java.io.IOException ioe ){
         ioe.printStackTrace();
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
