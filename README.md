a
=

A is a JMS testing/admin utility specialized for ActiveMQ.

Used to send, browse and put messages on queues.

[![Build Status](https://travis-ci.org/fmtn/a.svg?branch=master)](https://travis-ci.org/fmtn/a)


```
usage: java -jar a-<version>-with-dependencies.jar [-A] [-a] [-b <arg>]
       [-C <arg>] [-c <arg>] [-D <arg>] [-e <arg>] [-F <arg>] [-f <arg>]
       [-g] [-H <property=value>] [-I <property=value>] [-i <arg>] [-J
       <arg>] [-j] [-L <property=value>] [-l] [-M <arg>] [-n] [-O] [-o
       <arg>] [-P <arg>] [-p <arg>] [-R <arg>] [-r <arg>] [-s <arg>] [-T]
       [-t <arg>] [-U <arg>] [-w <arg>]
 -A,--amqp                     Set protocol to AMQP. Defaults to OpenWire
 -a,--artemis-core             Set protocol to ActiveMQ Artemis Core.
                               Defaults to OpenWire
 -b,--broker <arg>             URL to broker. defaults to:
                               tcp://localhost:61616
 -C,--copy-queue <arg>         Copy all messages from this to target.
                               Limited by maxBrowsePageSize in broker
                               settings (default 400).
 -c,--count <arg>              A number of messages to browse,get,move or
                               put (put will put the same message <count>
                               times). 0 means all messages.
 -D,--correlation-id <arg>     Set CorrelationID
 -e,--encoding <arg>           Encoding of input file data. Default UTF-8
 -F,--jndi-cf-name <arg>       Specify JNDI name for ConnectionFactory.
                               Defaults to connectionFactory. Use with -J
 -f,--find <arg>               Search for messages in queue with this
                               value in payload. Use with browse.
 -g,--get                      Get a message from destination
 -H <property=value>           use value for given String property. Can be
                               used several times.
 -I <property=value>           use value for given Integer property. Can
                               be used several times.
 -i,--priority <arg>           sets JMSPriority
 -J,--jndi <arg>               Connect via JNDI. Overrides -b and -A
                               options. Specify context file on classpath
 -j,--jms-headers              Print JMS headers
 -L <property=value>           use value for given Long property. Can be
                               used several times.
 -l,--list-queues              List queues and topics on broker (OpenWire
                               only)
 -M,--move-queue <arg>         Move all messages from this to target
 -n,--non-persistent           Set message to non persistent.
 -O,--openwire                 Set protocol to OpenWire. This is default
                               protocol
 -o,--output <arg>             file to write payload to. If multiple
                               messages, a -1.<ext> will be added to the
                               file. BytesMessage will be written as-is,
                               TextMessage will be written in UTF-8
 -P,--pass <arg>               Password to connect to broker
 -p,--put <arg>                Put a message. Specify data. if starts with
                               @, a file is assumed and loaded
 -R,--read-folder <arg>        Read files in folder and put to queue. Sent
                               files are deleted! Specify path and a
                               filename. Wildcards are supported '*' and
                               '?'. If no path is given, current directory
                               is assumed.
 -r,--reply-to <arg>           Set reply to destination, i.e. queue:reply
 -s,--selector <arg>           Browse or get with selector
 -T,--no-transaction-support   Set to disable transactions if not
                               supported by platform. I.e. Azure Service
                               Bus. When set to false, the Move option is
                               NOT atomic.
 -t,--type <arg>               Message type to put, [bytes, text, map] -
                               defaults to text
 -U,--user <arg>               Username to connect to broker
 -w,--wait <arg>               Time to wait on get operation. Default 50.
                               0 equals infinity
```

Example 1. Put message with payload "foobar" to queue q on local broker:
    
    $a -p "foobar" q

Example 2. Put message with payload of file foo.bar to queue q on local broker, also set a property
    
    $a -p "@foo.bar" -Hfoo=bar q

Example 3. Browse five messages from queue q.
 
    $a -c 5 q

Example 4. Put 100 messages to queue q (for load test etc)

    $a -p "foobar" -c 100 q

Example 5. Get message from queue and show JMS headers
    
    $a -g -j q

Example 6. Put file foo.bar as a byte message on queue q
    
    $a -p "@foo.bar" -t bytes q

Example 7. Put file foo.bar as text message on queue q, with encoding EBCDIC CP037 (any charset known on server/JVM should work)
    
    $a -p "@foo.bar" -e CP037 q
    
Example 8. Read all XML files in a folder input an put them on queue q. Files are deleted afterwards.

    $a -R "input/*.xml" q

Example 9. Put file foo.json as map message on queue q

    $a -p "@foo.json" -t map q

#Use AMQP 1.0
A defaults to ActiveMQ default protocol, OpenWire. You can also use AMQP 1.0.
In theory, it should work with all AMQP 1.0 compliant brokers. It does not work with older versions of AMQP.

    $a -A -b "amqp://guest:guest@localhost:5672" -p "foobar" q
    
    
#Azure Service Bus
Service Bus supports AMQP 1.0 so it's possible to use A to connect.
However, it does not support transactions, so the -T option has to be set to deal with that.

To connect, you will need a "username" and "password". The username will be the "shared access policy name".
The password is the URL-encoded key for that policy. These are found in the Azure portal.

Example command to send a message to Azure Service Bus:

	$a -A -T -b "amqps://mypolicyname:iAkywS...@mynamespace.servicebus.windows.net" -p "Test msg" q


A word of warning! There are some features not working with AMQP 1.0 in Service Bus. Some of which are mandatory to support the JMS API fully.
This means some of the features of A will not work - or behave strangely.

#Use Artemis Core
Use Artemis core protocol (HornetQ) with the -a option.

    $a -a -b "tcp://localhost:61616" -p "foobar" q

Please note that this won't auto deploy the queue in current versions of Artemis. Using OpenWire will autodeploy the queue.

#Use JNDI to connect
To connect in a protocol agnostic way, you can specify a JNDI file that points out the JMS provider and settings.

Simply create a jndi.properties file "at classpath". Then link to it jusing the -J (--jndi) option. Please name your
ConnectionFactory "connectionFactory". Otherwise, the name has to be supplied using the -F (--jndi-cf-name) option.

    $a -J jndi.properties -p "foobar" q

This way, you can even connect to non ActiveMQ/AMQP brokers. You simply need to provide a JNDI config and the client at classpath.

#Build

    $mvn install

#Make the jar runnable from *nix-shell as in examples:
1. copy the jar target/a-VERSION-with-dependencies.jar to someplace. i.e. ~/bin/
2. create a file called "a" on your path (~/bin/a or what have you)
```  
#!/bin/sh
java -jar ~/bin/a-1.2.0-jar-with-dependencies.jar "$@"
```
3. chmod +x a
4. Run a from any place.

#Make the jar runnable in windows console
1. copy the jar target/a-VERSION-with-dependencies.jar to someplace. i.e. c:\bin
2. create a file called "a.bat" on your path, i.e. c:\bin
```
@echo off
java -jar c:\bin\a-1.2.0-jar-with-dependencies.jar %*
```
3. Run from any place.


#Use SSL
Given you have a truststore and a keystore in JKS format, you can edit your a start script, or run it manually like this.
Note that the -Djavax parameters has to come before -jar. 
```
java -Djavax.net.ssl.keyStore=/Users/petter/client.jks -Djavax.net.ssl.keyStorePassword=password -Djavax.net.ssl.trustStore=/Users/petter/truststore.jks -Djavax.net.ssl.trustStorePassword=password -jar a-1.2.0-jar-with-dependencies.jar -b ssl://example.org:61618 MY.QUEUE 

```