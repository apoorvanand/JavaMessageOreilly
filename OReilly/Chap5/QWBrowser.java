/*
 * Copyright (C) 2000, O'Reilly & Associates, Inc.
 * The code in this file may be copied, distributed, and reused,
 * provided that you do not remove this copyright notice.
 * O'Reilly & Associates assumes no responsibility for damages
 * resulting from the use of this code.
 */

package chap5.b2b;

import java.util.StringTokenizer;
import java.util.Properties;
//import javax.naming.InitialContext;
import javax.jms.TopicConnectionFactory;
import javax.jms.QueueConnectionFactory;
import javax.jms.Topic;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.Session;
import javax.jms.TextMessage;

public class QWBrowser implements javax.jms.MessageListener{

    private javax.jms.TopicConnection tConnect = null;
    private javax.jms.TopicSession tSession = null;
    private javax.jms.TopicPublisher tPublisher = null;
   
    private javax.jms.QueueConnection qConnect = null;    
    private javax.jms.QueueSession qSession = null;
    private javax.jms.QueueReceiver qReceiver = null;
    private javax.jms.Queue receiveQueue = null;

    private javax.jms.Topic hotDealsTopic = null;
    private javax.jms.TemporaryTopic buyOrdersTopic = null;
    private String uname = null;

   public QWBrowser (String broker, String username, String password){
      try{
        TopicConnectionFactory tFactory = null;
        QueueConnectionFactory qFactory = null;
        InitialContext jndi = null;
        uname = username;
        
        Properties env = new Properties();
        // ... specify the JNDI properties specific to JNDI SPI being used
        env.put("BROKER",broker);
        jndi = new InitialContext(env);
                          
        tFactory = 
            (TopicConnectionFactory)jndi.lookup("TopicConnectionFactory");
        qFactory = 
            (QueueConnectionFactory)jndi.lookup("QueueConnectionFactory");
        tConnect = tFactory.createTopicConnection (username, password);
        qConnect = qFactory.createQueueConnection (username, password);

        tSession = 
            tConnect.createTopicSession(false,Session.AUTO_ACKNOWLEDGE);
        qSession = 
            qConnect.createQueueSession(false,Session.AUTO_ACKNOWLEDGE);
             
        hotDealsTopic = (Topic)jndi.lookup("Hot Deals");
        receiveQueue = (Queue)jndi.lookup("Reply Q");        

        tPublisher = tSession.createPublisher(hotDealsTopic);

        qReceiver = qSession.createReceiver(receiveQueue);
        ((progress.message.jclient.QueueReceiver)qReceiver).setPrefetchThreshold(0);
        ((progress.message.jclient.QueueReceiver)qReceiver).setPrefetchCount(1);

        // Now that setup is complete, start the Connection
        tConnect.start();
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
        javax.jms.StreamMessage message = tSession.createStreamMessage();
        message.writeString(dealDesc);
        message.writeString(itemDesc);
        message.writeFloat(oldPrice);
        message.writeFloat(newPrice);
                   
        message.setStringProperty("Username", username);
        message.setStringProperty("itemDesc", itemDesc);
                   
        message.setJMSReplyTo(receiveQueue);
                   
        tPublisher.publish(
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
    private int examineQueue(int inStockQty)
    {
        int cnt = 0;
        int bestQty = 0;
        try{
            // qConnect.start();
            System.out.println("In Stock QTY: " + inStockQty);
            System.out.print ( "Creating QueueBrowser..." );
            javax.jms.QueueBrowser browser 
                = qSession.createBrowser(receiveQueue);
            System.out.println ("[done]");

            java.util.Enumeration e = browser.getEnumeration();		
            while(e.hasMoreElements()){
                System.out.print(" --> getting message " 
                + String.valueOf(++cnt) + "...");
                javax.jms.TextMessage message = 
                    (javax.jms.TextMessage) e.nextElement();
                System.out.println("[" + message.getText() + "]");
                if (message != null){
                    int orderQty = message.getIntProperty("QTY");
                    if( orderQty > bestQty && orderQty <= inStockQty)
                        bestQty = orderQty;
                }
            }
            // Free any resources in the browser.
            browser.close();
        }catch( javax.jms.JMSException jmse ){
            jmse.printStackTrace();
        }
        System.out.println("\nBestQty: " + bestQty);
        return bestQty;
    }
public void processInput(){
    try{
        // Read all standard input and send it as a message.
        java.io.BufferedReader stdin = new java.io.BufferedReader
            (new java.io.InputStreamReader( System.in ) );
        while ( true ){
            System.out.println ("Enter: Item, Old Price, New Price ");
            System.out.println("\ne.g. Bowling Shoes, 100.00, 55.00");
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
                                          
                publishPriceQuotes(dealDesc,uname,
                    itemDesc, oldPrice,newPrice);
                int inStockQty = 
                    (int)(java.lang.Math.random() * (double)1000);                                      
                int bestQty = examineQueue(inStockQty);
                qConnect.start();
                javax.jms.TextMessage textMessage = null;
                while( true )
                {
                    textMessage = 
                        (javax.jms.TextMessage)qReceiver.receive(1000);
                    if ( textMessage == null ){
                        qConnect.stop();
                        break;  // no more messages to get
                    }

                    String text = textMessage.getText();
                    int qty = textMessage.getIntProperty("QTY");
                    System.out.println("\nOrder received - "+text+
                        " from " + textMessage.getJMSCorrelationID());

                    // could have missed the first one
				    if( qty > bestQty && qty <= inStockQty)
					    bestQty = qty;
                    if (qty == bestQty){
                        System.out.println("Fulfilling order");
                        // Do some processing to fulfill order
                    }else{
                        System.out.println("Placing in BACK-ORDER status");
                        // Do some processing to create BACK-ORDER status
                    }
                }
            }else{
                this.exit();
            }
        }
    }catch (javax.jms.JMSException jmse){
        jmse.printStackTrace(); 
    }catch( java.io.IOException ioe ){
        ioe.printStackTrace();
    //}catch( java.lang.InterruptedException ie ){
    //    ie.printStackTrace();
    }
}
   public void exit(){
      try{
        tConnect.close();
        qConnect.close();
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
        System.out.println("java QWholesaler broker username password");
        return;
    }
          
    QWBrowser wholesaler = new QWBrowser(broker, username, password);
    wholesaler.processInput();
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