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
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

public class Retailer implements javax.jms.MessageListener{

    private javax.jms.TopicConnection connect = null;
    private javax.jms.TopicSession session = null;
    private javax.jms.TopicPublisher publisher = null;
    private javax.jms.Topic hotDealsTopic = null;
    private javax.jms.TopicSubscriber subscriber = null;
    private static boolean inRollback = false;
    private static boolean rollbackOnly = false;

    public Retailer( String broker, String username, String password){
        try{
            TopicConnectionFactory factory = null;
            InitialContext jndi = null;

            Properties env = new Properties();
            // ... specify the JNDI properties specific to the JNDI SPI being used
            env.put("BROKER",broker);
            jndi = new InitialContext(env);

            factory =
                (TopicConnectionFactory)jndi.lookup("TopicConnectionFactory");

            connect = factory.createTopicConnection (username, password);
            connect.setClientID(username);

            session =
                connect.createTopicSession(true,Session.AUTO_ACKNOWLEDGE);

            hotDealsTopic = (Topic)jndi.lookup("Hot Deals");

            subscriber = session.createDurableSubscriber(hotDealsTopic,
                    "Hot Deals Subscription");
            publisher = session.createPublisher(null);

            subscriber.setMessageListener(this);
            connect.start();

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
            boolean redelivered = message.getJMSRedelivered();
            StreamMessage strmMsg = (StreamMessage)message;
            if( redelivered ){
                System.out.println("\nMessage redelivered, inRollback: "
                    + inRollback + " rollbackOnly: " + rollbackOnly );
                strmMsg.reset();
            }

            if( strmMsg.propertyExists("SEQUENCE_MARKER") ){
                String sequence = strmMsg.getStringProperty( "SEQUENCE_MARKER" );
                if( sequence.equalsIgnoreCase("END_SEQUENCE") )
                {
                    if( redelivered && inRollback ){  // at the end, start fresh
                        inRollback = false;
                        rollbackOnly = false;
                        session.commit();
                    }
                    else if( rollbackOnly ){
                        inRollback = true;
                        session.rollback();
                    }
                    else
                        session.commit();
                }
                return;
            }
            if( rollbackOnly )
                return; // ignore all other messages while in rollback mode

            String dealDesc = strmMsg.readString();
            String itemDesc = strmMsg.readString();
            float oldPrice = strmMsg.readFloat();
            float newPrice = strmMsg.readFloat();
            System.out.println("Received Hot Buy :"+dealDesc);

            // if price reduction greater than 10 percent, buy
            if (newPrice == 0 || oldPrice / newPrice > 1.1){
                System.out.println ("\nBuying " + count + " " + itemDesc);

                TextMessage textMsg = session.createTextMessage();
                textMsg.setText(count + " " + itemDesc );

                javax.jms.Topic buyTopic =
                    (javax.jms.Topic)message.getJMSReplyTo();

                //publisher = session.createPublisher(buytopic);

                textMsg.setJMSCorrelationID("DurableRetailer");

                publisher.publish(
                    buyTopic,
                    textMsg,
                    javax.jms.DeliveryMode.PERSISTENT,
                    javax.jms.Message.DEFAULT_PRIORITY,
                    1800000);
            }else{
                System.out.println ("\nBad Deal.  Not buying");
                //session.rollback();
                rollbackOnly = true;
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
                subscriber.close();
                session.unsubscribe("Hot Deals Subscription");
            }
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
            System.out.println
            ("java Retailer broker username password");
            return;
        }

        Retailer retailer  = new Retailer(broker, username, password);

        try{
            System.out.println("\nRetailer application started.\n");
            // Read all standard input and send it as a message.
            java.io.BufferedReader stdin =
                new java.io.BufferedReader
                ( new java.io.InputStreamReader( System.in ) );
            while ( true ){
                String s = stdin.readLine();
                if ( s == null || s.length() == 0 )retailer.exit(null);
                else if ( s.equalsIgnoreCase("unsubscribe") )
                    retailer.exit ( s );
                else if ( s.equalsIgnoreCase("commit") )
                {
                    try{
                        System.out.println("\nGonna Commit");
                        retailer.session.commit();
                    }catch( javax.jms.JMSException jmse ){jmse.printStackTrace();}
                }
                else if ( s.equalsIgnoreCase("rollback") )
                {
                    try{
                        System.out.println("\nRolling back");
                        retailer.session.rollback();
                    }catch( javax.jms.JMSException jmse ){jmse.printStackTrace();}
                }
            }
        }catch ( java.io.IOException ioe ){
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
