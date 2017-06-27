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
    private javax.jms.TopicPublisher tPublisher = null;

    private javax.jms.Topic hotDealsTopic = null;
    private javax.jms.TopicSubscriber tSubscriber = null;
    private static int msgCount = 0;

    public QRetailer( String broker, String username, String password){
        try{
            TopicConnectionFactory tFactory = null;
            QueueConnectionFactory qFactory = null;
            InitialContext jndi = null;

            Properties env = new Properties();
            // ... specify the JNDI properties specific to the JNDI SPI being used
            env.put("BROKER", broker);
            jndi = new InitialContext(env);

            tFactory =
                (TopicConnectionFactory)jndi.lookup("TopicConnectionFactory");
            qFactory =
                (QueueConnectionFactory)jndi.lookup("QueueConnectionFactory");

            tConnect = tFactory.createTopicConnection (username, password);
            qConnect = qFactory.createQueueConnection (username, password);
            tConnect.setClientID(username);
            qConnect.setClientID(username);

            tSession =
                tConnect.createTopicSession(false,Session.CLIENT_ACKNOWLEDGE);
            qSession =
                qConnect.createQueueSession(false,javax.jms.Session.AUTO_ACKNOWLEDGE);

            hotDealsTopic = (Topic)jndi.lookup("Hot Deals");

            tSubscriber = tSession.createDurableSubscriber(hotDealsTopic,
                    "Hot Deals Subscription");
            tSubscriber.setMessageListener(this);
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
        int count = 1000;
        try{

            StreamMessage strmMsg = (StreamMessage)message;
            //must reset the message stream so that it can be read from the beginning
            if( strmMsg.getJMSRedelivered() )
                strmMsg.reset();
            String dealDesc = strmMsg.readString();
            String itemDesc = strmMsg.readString();
            float oldprice = strmMsg.readFloat();
            float newprice = strmMsg.readFloat();
            System.out.println("Received Hot Buy :"+dealDesc);


            if ( saveDesc == null){
                if (message.getJMSRedelivered())
                    processCompensatingTransaction();
                processInterimMessages( itemDesc );
                return;
            }

            // if price reduction greater than 10 percent, buy
            if ((newprice == 0 || oldprice / newprice > 1.1)){
                TextMessage textMsg = tSession.createTextMessage();
                textMsg.setText(count + " " + saveDesc + ", "
                    + count + " " + itemDesc );

                textMsg.setJMSCorrelationID("DurableRetailer");

                Queue buyQueue = (Queue)message.getJMSReplyTo();

                System.out.println ("\nBuying " + count + " "
                    + saveDesc + " " + count + " " + itemDesc);

                qSender = qSession.createSender(buyQueue);
                qSender.send( textMsg,
                                javax.jms.DeliveryMode.PERSISTENT,
                                javax.jms.Message.DEFAULT_PRIORITY,
                                1800000);
                // acknowledge the original message
                try{
                    System.out.println("\nAcknowledging messages");
                    message.acknowledge();
                    System.out.println("\nMessage acknowledged");
                    saveDesc = null;
                }catch (javax.jms.JMSException jmse){
                    System.out.println("\nAcknowledgement failed." +
                    "\nProcessing compensating transaction for interim messages");
                    processCompensatingTransaction();
                }
            }else{
                System.out.println ("\nBad Deal.  Not buying");
            }
        }catch (javax.jms.JMSException jmse){
            jmse.printStackTrace();
        }
    }
    private String saveDesc = null;
    private void processInterimMessages(String itemDesc)
    {
        saveDesc = itemDesc;
    }
    private void processCompensatingTransaction()
    {
        System.out.println("Processing compensating");
        saveDesc = null;  // null out "saved" work
    }
    private void exit(String s){
        try {
            if ( s != null &&
                s.equalsIgnoreCase("unsubscribe"))
            {
                tSubscriber.close();
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