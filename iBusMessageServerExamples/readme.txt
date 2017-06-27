
README
======

The ZIP archive iBusMessageServerExamples.zip contains example applications
that relate to those presented in the book 'Java Message Service' by O'Reilly
and Associates. The implementation is derived from the initial code examples 
provided by O'Reilly and Associates:

    ftp://ftp.oreilly.com/pub/examples/java/javmesser


All examples are grouped in packages that refer to the chapters in the 'Java
Message Server' book:

    Chapter 2: Developing a Simple Example

    Chapter 4: Publish and Subscribe

    Chapter 5: Point-to-point

    Chapter 6: Guaranteed Messaging, Transactions, Acknowledgements and Failures


This set of examples has some differences compared to the set of examples 
provided in 'Java Message Service'. See section Notes for more detailled 
information.


The examples contained and described in this archive are adapted to and tested 
with Softwired's JMS implementation, iBus//MessageServer Version 4.1.0, on a 
JRE 2 platform for Linux.


Get a free trial distribution of iBus//MessageServer from Softwired:

    http://www.softwired-inc.com/products/serverstandard/serverstandard.html

If you want to install iBus//MessageServer simply to run the examples, we
recommed iBus//MessageServer Standard Edition. The Standard Edition can be used
without further administration.

Of course, you may also use iBus//MessageServer Business Edition. However, to
run the examples, you then need to administer the access control list 
(i.e. you have to create some Chat users). To administer access control,
download and install the Administration Client.

Read the documentation contained in the download package about setup and
installation of an iBus//MessageServer. 


Visit us at http://www.softwired-inc.com to get more information.

For questions, please do not hesitate to contact:

    info@softwired-inc.com

Please send comments and bug reports to:

    support@softwired-inc.com



How to run the examples
-----------------------

In order to compile and run the examples you need to install:

    - JDK 1.1 (or higher). See http://java.sun.com for download info.
    - iBus//MessageServer from Softwired. See above for download info.

The following steps assume a UNIX environment.

1.  Open a shell and set its CLASSPATH environment variable.

        > CLASSPATH=$EXAMPLE_CLASSES:$IBUS/client/lib/msrvClt.jar
        > CLASSPATH=$CLASSPATH:$IBUS/client/lib/jndi.jar
        > export CLASSPATH

        where

            EXAMPLE_CLASSES is the path to the example class directory
            ( i.e. $HOME/projects/ibus_msrv/classes ).

            IBUS is the path to iBus//MessageServer base directory
            ( i.e. /usr/local/java/iBusMessageServer4.1.0 ).

2.  Change to the directory where the source code resides.

3.  Compile the example code.

    I.e. to compile the example of chapter 2, type:

        > javac -d $EXAMPLE_CLASSES *java

    This will first create a directory structure for the class files and then,
    generate the class file Chat.class into that directory.

4.  Start the example program.

    I.e. to run the example of chapter 2, type:

        > cd $EXAMPLE_CLASSES
        > java chap2.chat.Chat topic-name 


Notes
-----

We have tried to remain true to the examples in the book, but have made minor 
changes that enhance robustness or that avoid compilation problems in some 
development environments.


The class InitialContext has been adopted to return classes from the 
ch.softwired.jms package. It is now an inner class to avoid naming conflicts
(i.e. JBuilder refused to compile Wholesaler and Retailer from chapter 2 if
included in package chap2.chat).


Client IDs must be configured or set explicitly. 
Otherwise, the MessageServer will give a warning and assign a unique ID,
such as UNKNOWN_CLIENT.rl.980869363741. To avoid this, we introduced an explicit
ID assignment:

   connection.setClientID( username );


In chapter 2, 

a lookup() for a JMS topic may cause a ClassCastException which is caught.
A message explains the problem, and the client terminates.

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


If the main() argument list is wrong, the client terminates.
One argument only, that is the topic name, is accepted. In that case,
username and password are set to "guest".


In chapter 4 (and subsequently),

the Wholesaler should not terminate just because of invalid deal descriptors.
NoSuchElementException and NumberFormatException are caught.

The Retailer now terminates on ENTER (not C-c):

    if ( s == null || s.length() == 0 ) retailer.exit(null);


In chapter 5 (and subsequently),

client IDs must not only be configured or set explicitly, they must be unique
as well:

    tConnect.setClientID(username + "-topic" );
    qConnect.setClientID(username + "-queue" ); 


Non-JMS, SonicMQ-specific casts such as 

    ((progress.message.jclient.QueueReceiver)qReceiver).setPrefetchThreshold(0);
    ((progress.message.jclient.QueueReceiver)qReceiver).setPrefetchCount(1);

have been deleted or uncommented.


In chapter 6,

the Wholesaler's implementation of establishConnection() and onException()
has changed. The client's JMS runtime of iBus//MessageServer will try to
reestablish a lost connection. The file config.ibus.jms.txt defines the number
of milliseconds to wait between connection attempts, and the number of connect
attempts. If the JMS runtime fails, a ch.softwired.jms.ServerDiedException is
thrown and then, the responsibility to reconnect is taken over by the client
application. This is done by the check:

    if ( "ch.softwired.jms.ServerDiedException".equals
        ( jmse.getClass().getName() ) ) {
        System.err.println( "\nServer has died, client still trying ..." );
        establishConnection(mBroker, mUsername, mPassword);
    }

In addtion, Wholesaler.establishConnection() explicitely closes the connection
to avoid connection ID conflicts:

    // Close an already open connection in both the client's JMS runtime
    // and on the MessageServer itself, if possible (otherwise, warnings will
    // notify the client that the MessageServer has died).
    if ( connect != null ) {
         connect.close();
         connect = null;
    }


To avoid a StringIndexOutOfBoundsException i.e. if you typed "EN":

    if( "END".equalsIgnoreCase( dealDesc ) ) {
        wholesaler.sendSequenceMarker( "END_SEQUENCE" );
    } 
