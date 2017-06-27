EXAMPLES FOR THE BOOK "Java Message Service"

The information in this readme file describes the Java example source files 
contained in the download archive package jms_samples.zip.

The example source files are presented in folders for the chapter
where the examples are discussed:

- Chapter 2: Developing a Simple Example
   - Chat.java
- Chapter 4: Publish and Subscribe
   - Retailer.java
   - Wholesaler.java
- Chapter 5: Point-to-point
   - QRetailer.java
   - QWholesaler.java
   - Retailer.java
   - Wholesaler.java
- Chapter 6: Guaranteed Messaging, Transactions, Acknowledgements and Failures
   - QRetailer.java
   - QWholesaler.java
   - Retailer.java
   - Wholesaler.java

REQUIREMENTS
============
To use the examples you need to:

- Access, install, and run a JMS implementation and then start the message server.

- Provide a toolset that can edit and compile Java source files. This could be an 
  integrated development environment or simply a text editor and the javac.exe in 
  a Java SDK. Note that if you use javac you are advised to use the -d switch to 
  propagate the sub-directories in the sample source files.

- Provide a Java runtime environment (JRE) appropriate for the JMS provider.

- Determine the semantics the JMS provider expects when you create a 
  ConnectionFactory. An InitialContext is included in each sample file that will 
  provide a context for connection to localhost on port 2506. You may need to replace
  the InitialContext section of the source file for  your JMS provider.  

THE SONICMQ SPECIFIC vs. "GENERIC" EXAMPLES
===========================================

The chap5\QWBrowser.java and chap5\QWholesaler.java examples contain a SonicMQ extension 
that controls the amount of messages that are fetched from the queue at one time, as illustrated by the following
lines of code:
        qReceiver = qSession.createReceiver(receiveQueue);
        ((progress.message.jclient.QueueReceiver)qReceiver).setPrefetchThreshold(0);
        ((progress.message.jclient.QueueReceiver)qReceiver).setPrefetchCount(1);

The "generic" examples don't have this.

The chap6\Wholesaler.java has some SonicMQ extensions which have to do with detecting
loss of connection and the onException handler.
        // SonicMQ vendor specific extension.
        // Ping the broker periodically to ensure that the connection
        // is still active.
        ((progress.message.jclient.TopicConnection)connect).setPingInterval(30);

	...

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

See also "USING JNDI" below.

USING JNDI LOOKUPS WITH THESE EXAMPLES
======================================
Each of the SonicMQ specific example files include a local InitialContext class.
This class uses the SonicMQ extension API's for obtaining a ConnectionFactory,
and creating a Topic or Queue.  While SonicMQ also fully supports JNDI access,
it does not provide an automatic JNDI SPI implementation out of the box.  If
you want more information on how to use SonicMQ with JNDI access to an LDAP store,
refer to the SonicMQ documentation, or get the JNDI whitepaper from the
SonicMQ Developer's Exchange web site http://www.developers.SonicMQ.com.

The "Generic" version of these examples do not include this stub class.  However,
the vendor specific properties still need to be specified if you are using a
JMS implementation other than SonicMQ.  If the vendor you are using also supports
a vendor extension dynamic API, you may want to consider modifying the InitialContext
class included with the SonicMQ specific examples, and plug in the other JMS vendor's 
dynamic API.



COMPILING THE EXAMPLES
======================
If you are using SonicMQ, and you have placed the files in the SonicMQ samples
directory, here are the instructions for compiling.  From the individual, chap2, chap4,
chap5, and chap6 directories, execute javac.exe with the following command line.

<sonicInstalldir>\samples\OReilly\chap#><javac> -classpath "<jdk
classpath>;..\..\..\lib\client.jar;..\..\..\lib\jms.jar;..\..\..\lib\jndi.ja
r;." -d ..\ <Sourcefile>



USING THE EXAMPLES
==================
The general instructions for using the examples are:
- Edit the example to add the appropriate ConnectionFactory instruction.
- Compile the Java file successfully.

For each instance of the compiled file you want to run:
   - Position a console window to the root level of the samples directory.
   - Set the CLASSPATH environment variables for the Java runtime.
   - Set the PATH environment variable to /Java/bin directory for the Java runtime.
   - Enter the run command in the form that includes the path to the class: 
        java <example_name> parameter1 parameter2 ...
     
     For example:
        java chap2.chat.Chat aTopic aUser aPassword

MODIFYING THE EXAMPLES
======================
The book suggests modifications you can make to the source file to explore other features.
To implement those features:
- Edit the source file as described in the book (in a new folder if you want to preserve 
  the existing source and class files).
- Compile the modified example and then run it.

The examples for Chapters 4, 5 and 6 that have similar names are distinct versions 
of the example that already reflect changes to the preceding examples to implement the 
features under discussion.


NOTES
=====
In examples where you set up Durable Subscriptions, such as the Retailer, exiting the console
window by pressing Ctrl+C might leave published messages waiting for the durable subscriber.
That situation could result in an expected set of delivered messages when that sample runs again.
The standard JMS technique to avoid this situation is to explicitly unsubscribe.
In a console window, type "unsubscribe" and press Enter.

In the Retailer example in chapter 6, a single line is missing in the printed text.  The 
source code included with these examples has the correct behavior. At this location 
toward the middle of the file:
                ...
                if( redelivered && inRollback ){  // at the end, start fresh
                        inRollback = false;
                        rollbackOnly = false;
			session.commit();  <---- This line has been added
                    }
                    else if( rollbackOnly )
                ...

