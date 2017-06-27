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
import javax.naming.*;
import javax.jms.TopicConnectionFactory;
import javax.jms.QueueConnectionFactory;
import javax.jms.Topic;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

public class QRetailer implements javax.jms.MessageListener{

    private javax.jms.QueueConnection qConnect = null;
    private javax.jms.QueueSession qSession = null;
    private javax.jms.QueueSender qSender = null;

    private javax.jms.TopicConnection tConnect = null;
    private javax.jms.TopicSession tSession = null;

    private javax.jms.Topic hotDealsTopic = null;
    private javax.jms.TopicSubscriber tsubscriber = null;
    private static String uname = null;

    public QRetailer( String broker, String username, String password){
        try{
            TopicConnectionFactory tFactory = null;
            QueueConnectionFactory qFactory = null;
            InitialContext jndi = null;
            uname = username;

            Properties env = new Properties();
            // ... specify the JNDI properties specific to the JNDI SPI being used
            env.put("BROKER", broker);
            jndi = new InitialContext(env);

            tFactory =
                (TopicConnectionFactory)jndi.lookup("TopicConnectionFactory");
            qFactory =
                (QueueConnectionFactory)jndi.lookup("QueueConnectionFactory");

            tConnect =
                tFactory.createTopicConnection (username, password);
            qConnect =
                qFactory.createQueueConnection (username, password);
            tConnect.setClientID(username);
            qConnect.setClientID(username);

            tSession =
                tConnect.createTopicSession(false,
                    Session.AUTO_ACKNOWLEDGE);
            qSession =
                qConnect.createQueueSession(false,
                    javax.jms.Session.AUTO_ACKNOWLEDGE);

            hotDealsTopic = (Topic)jndi.lookup("Hot Deals");
            hotDealsTopic = tSession.createTopic ("Hot Deals");

            tsubscriber = tSession.createDurableSubscriber(hotDealsTopic,
                    "Hot Deals Subscription");
            tsubscriber.setMessageListener(this);
            tConnect.start();

        }catch (javax.jms.JMSException jmse){
            jmse.printStackTrace();
            System.exit(1);
        }catch(javax.naming.NamingException jne){
         jne.printStackTrace(); System.exit(1);
        }
    }
    public void onMessage(javax.jms.Message aMessage){
        try{
            autoBuy(aMessage);
        }catch (java.lang.RuntimeException rte){
            rte.printStackTrace();
        }
    }
    private void autoBuy (javax.jms.Message message){
        try{
            StreamMessage strmMsg = (StreamMessage)message;
            String dealDesc = strmMsg.readString();
            String itemDesc = strmMsg.readString();
            float oldPrice = strmMsg.readFloat();
            float newPrice = strmMsg.readFloat();
            System.out.println("Received Hot Buy :"+dealDesc);

            // if price reduction greater than 10 percent, buy
            if (newPrice == 0 || oldPrice / newPrice > 1.1){
                int count = (int)(java.lang.Math.random() * (double)1000);
                System.out.println ("\nBuying " + count + " " + itemDesc);

                TextMessage textMsg = tSession.createTextMessage();
                textMsg.setText(count + " " + itemDesc );
                textMsg.setIntProperty("QTY", count);

                textMsg.setJMSCorrelationID(uname);

                Queue buyQueue = (Queue)message.getJMSReplyTo();

	            qSender = qSession.createSender(buyQueue);
                qSender.send( textMsg,
                                javax.jms.DeliveryMode.PERSISTENT,
                                javax.jms.Message.DEFAULT_PRIORITY,
                                1800000);
            }else{
                System.out.println ("\nBad Deal.  Not buying");
            }
        }catch (javax.jms.JMSException jmse){
            jmse.printStackTrace();
        }
    }
    private void exit(String s){
        try {
            if ( s != null &&
                s.equalsIgnoreCase("unsubscribe"))
            {
                tsubscriber.close();
                tSession.unsubscribe("Hot Deals Subscription");
            }
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
            System.out.println
            ("java QRetailer broker username password");
            return;
        }

        QRetailer retailer  = new QRetailer(broker, username, password);

        try{
            System.out.println("\nRetailer application started.\n");
            // Read all standard input and send it as a message.
            java.io.BufferedReader stdin =
                new java.io.BufferedReader
                ( new java.io.InputStreamReader( System.in ) );
            while ( true ){
                String s = stdin.readLine();
                if ( s == null )retailer.exit(null);
                else if ( s.equalsIgnoreCase("unsubscribe") )
                    retailer.exit ( s );
            }
        }catch ( java.io.IOException ioe ){
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