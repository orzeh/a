package co.nordlander.a;

import static co.nordlander.a.A.CMD_COPY_QUEUE;
import static co.nordlander.a.A.CMD_COUNT;
import static co.nordlander.a.A.CMD_GET;
import static co.nordlander.a.A.CMD_MOVE_QUEUE;
import static co.nordlander.a.A.CMD_PRIORITY;
import static co.nordlander.a.A.CMD_PUT;
import static co.nordlander.a.A.CMD_READ_FOLDER;
import static co.nordlander.a.A.CMD_WAIT;
import static co.nordlander.a.A.CMD_TYPE;
import static co.nordlander.a.A.TYPE_MAP;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.MapMessage;

import org.apache.activemq.broker.BrokerService;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A base class with all the test cases.
 * The actual transport protocol has to be implemented as well as the broker implementation.
 * This is done in the real test classes. They could test any JMS complaint protocol and broker.
 *
 * This makes it easy to test that the basic functionality works with different ActiveMQ configurations.
 *
 * Created by petter on 2015-01-30.
 */
public abstract class BaseTest {

    protected static final String LN = System.getProperty("line.separator");
    protected static final long TEST_TIMEOUT = 2000L;
    protected Connection connection;
    protected Session session;
    protected ConnectionFactory cf;
    protected ExecutorService executor;
    protected A a;
    protected ATestOutput output;
    protected Destination testTopic, testQueue, sourceQueue, targetQueue;
    protected TextMessage testMessage;

    @Autowired
    protected BrokerService amqBroker;

    protected abstract ConnectionFactory getConnectionFactory();
    protected abstract String getConnectCommand();
    protected abstract void clearBroker() throws Exception;
    
    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();


    @Before
    public void setupJMS() throws Exception {
        cf = getConnectionFactory();
        connection = cf.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        executor = Executors.newSingleThreadExecutor();
        a = new A();
        output = new ATestOutput();
        a.output = output;

        clearBroker();

        testTopic = session.createTopic("TEST.TOPIC");
        testQueue = session.createQueue("TEST.QUEUE");
        sourceQueue = session.createQueue("SOURCE.QUEUE");
        targetQueue = session.createQueue("TARGET.QUEUE");
        testMessage = session.createTextMessage("test");
        connection.start();
    }

    @After
    public void disconnectJMS() throws JMSException {
        session.close();
        connection.close();
        executor.shutdown();
    }

    @Test
    public void testPutQueue() throws Exception{
        String cmdLine = getConnectCommand() + "-" + CMD_PUT + " \"test\"" + " TEST.QUEUE";
        System.out.println("Testing cmd: " + cmdLine);
        a.run(cmdLine.split(" "));
        MessageConsumer mc = session.createConsumer(testQueue);
        TextMessage msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertEquals("test",msg.getText());
    }

    @Test
    public void testPutWithPriority() throws Exception{
        final int priority = 6;
        String cmdLine = getConnectCommand() + "-" + CMD_PRIORITY +" " + priority + " -" + CMD_PUT + "\"test\""
                + " TEST.QUEUE";
        a.run(cmdLine.split(" "));
        MessageConsumer mc = session.createConsumer(testQueue);
        TextMessage msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertEquals("test",msg.getText());
        assertEquals(priority,msg.getJMSPriority());
    }

    @Test
    public void testPutTopic() throws Exception{
        String cmdLine = getConnectCommand() + "-" + CMD_PUT + "\"test\"" + " topic://TEST.TOPIC";
        Future<TextMessage> resultMessage = executor.submit(new Callable<TextMessage>(){
            public TextMessage call() throws Exception {
                MessageConsumer mc = session.createConsumer(testTopic);
                return (TextMessage)mc.receive(TEST_TIMEOUT);
            }
        });
        a.run(cmdLine.split(" "));
        assertEquals("test",resultMessage.get().getText());
    }

    @Test
    public void testGetQueue() throws Exception{
        MessageProducer mp = session.createProducer(testQueue);
        mp.send(testMessage);
        String cmdLine = getConnectCommand() + "-" + CMD_GET + " -" +
                CMD_WAIT + " 2000" + " TEST.QUEUE";
        a.run(cmdLine.split(" "));
        String out = output.grab();
        assertTrue("Payload test expected",out.contains("Payload:"+LN+"test"));
    }

    @Test
    public void testGetTopic() throws Exception{
        final String cmdLine = getConnectCommand() + "-" + CMD_GET + " -" +
                CMD_WAIT + " 4000" + " topic://TEST.TOPIC";
        Future<String> resultString = executor.submit(new Callable<String>(){
            public String call() throws Exception {
                a.run(cmdLine.split(" "));
                return output.grab();
            }
        });
        Thread.sleep(300); // TODO remove somehow?
        MessageProducer mp = session.createProducer(testTopic);
        mp.send(testMessage);
        String result = resultString.get();
        assertTrue("Payload test expected",result.contains("Payload:"+LN+"test"));
    }

    /**
     * Test that all messages are copied (not moved) from one queue to the other.
     * @throws Exception
     */
    @Test
    public void testCopyQueue() throws Exception{
        final String cmdLine = getConnectCommand() + "-" + CMD_COPY_QUEUE + " SOURCE.QUEUE TARGET.QUEUE";
        MessageProducer mp = session.createProducer(sourceQueue);
        mp.send(testMessage);
        mp.send(testMessage);
        a.run(cmdLine.split(" "));
        MessageConsumer mc = session.createConsumer(sourceQueue);
        TextMessage msg = null;
        // Verify messages are left on source queue
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNull(msg);
        // Verify messages are copied to target queue
        mc = session.createConsumer(targetQueue);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNull(msg);
    }

    /**
     * Test that all messages are moved from one queue to the other.
     * @throws Exception
     */
    @Test
    public void testMoveQueue() throws Exception{
        final String cmdLine = getConnectCommand() + "-" + CMD_MOVE_QUEUE + " SOURCE.QUEUE TARGET.QUEUE";
        MessageProducer mp = session.createProducer(sourceQueue);
        mp.send(testMessage);
        mp.send(testMessage);
        a.run(cmdLine.split(" "));
        MessageConsumer mc = session.createConsumer(sourceQueue);
        TextMessage msg = null;
        // Verify NO messages are left on source queue
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNull(msg);
        // Verify messages are moved to target queue
        mc = session.createConsumer(targetQueue);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNull(msg);
    }
    
    /**
     * Test that all messages are moved from one queue to the other.
     * Input count = 0
     * @throws Exception
     */
    @Test
    public void testMoveZeroCountQueue() throws Exception{
        final String cmdLine = getConnectCommand() + "-" + CMD_MOVE_QUEUE + " SOURCE.QUEUE -" + CMD_COUNT + " 0 TARGET.QUEUE";
        MessageProducer mp = session.createProducer(sourceQueue);
        mp.send(testMessage);
        mp.send(testMessage);
        a.run(cmdLine.split(" "));
        MessageConsumer mc = session.createConsumer(sourceQueue);
        TextMessage msg = null;
        // Verify NO messages are left on source queue
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNull(msg);
        // Verify messages are moved to target queue
        mc = session.createConsumer(targetQueue);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNull(msg);
    }

   /**
     * Test that all messages but one message is moved from one queue to the other.
     * @throws Exception
     */
    @Test
    public void testMoveCountQueue() throws Exception{
        final String cmdLine = getConnectCommand() + "-" + CMD_MOVE_QUEUE + " SOURCE.QUEUE " + "-c" + " 4 TARGET.QUEUE";
        MessageProducer mp = session.createProducer(sourceQueue);

        mp.send(testMessage);
        mp.send(testMessage);
        mp.send(testMessage);
        mp.send(testMessage);
        mp.send(testMessage);
	
        a.run(cmdLine.split(" "));
        MessageConsumer mc = session.createConsumer(sourceQueue);
        TextMessage msg = null;

        // Verify 1 messages are left on source queue
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);

        // Verify NO messages are left on source queue
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNull(msg);

        // Verify 4 messages is moved to target queue
        mc = session.createConsumer(targetQueue);

        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNotNull(msg);

	
        // Verify NO messages are left on target queue
        msg = (TextMessage)mc.receive(TEST_TIMEOUT);
        assertNull(msg);
    }


    @Test
    public void testGetCount() throws Exception{
        final String cmdLine = getConnectCommand() + "-" + CMD_GET + " -" + CMD_COUNT + "2 TEST.QUEUE";
        MessageProducer mp = session.createProducer(testQueue);
        mp.send(testMessage);
        mp.send(testMessage);
        a.run(cmdLine.split(" "));
        String out = output.grab().replaceFirst("Operation completed in .+","");

        final String expectedOut = "-----------------" + LN +
                "Message Properties" + LN +
                "Payload:" + LN +
                "test" + LN +
                "-----------------" + LN +
                "Message Properties" + LN +
                "Payload:" + LN +
                "test" + LN + LN;
        assertEquals(expectedOut,out);
    }

    @Test
    public void testMoveSelector() throws Exception{
        final String cmdLine = getConnectCommand() + "-" + CMD_MOVE_QUEUE + " SOURCE.QUEUE -s identity='theOne' TARGET.QUEUE";
        MessageProducer mp = session.createProducer(sourceQueue);

        Message theOne = session.createTextMessage("theOne"); // message
        theOne.setStringProperty("identity","theOne");
        Message theOther = session.createTextMessage("theOther"); // message
        theOther.setStringProperty("identity","theOther");

        mp.send(theOne);
        mp.send(theOther);

        a.run(cmdLine.split(" "));
        List<TextMessage> msgs = getAllMessages(session.createConsumer(sourceQueue));
        assertEquals(1,msgs.size());
        assertEquals("theOther",msgs.get(0).getText());

        msgs = getAllMessages(session.createConsumer(targetQueue));
        assertEquals(1,msgs.size());
        assertEquals("theOne",msgs.get(0).getText());
    }

    @Test
    public void testCopySelector() throws Exception{
        final String cmdLine = getConnectCommand() + "-" + CMD_COPY_QUEUE + " SOURCE.QUEUE -s \"identity='the One'\" TARGET.QUEUE";
        MessageProducer mp = session.createProducer(sourceQueue);

        Message theOne = session.createTextMessage("theOne"); // message
        theOne.setStringProperty("identity","the One");
        Message theOther = session.createTextMessage("theOther"); // message
        theOther.setStringProperty("identity","theOther");

        mp.send(theOne);
        mp.send(theOther);

        a.run(splitCmdLine(cmdLine));
        List<TextMessage> msgs = getAllMessages(session.createConsumer(sourceQueue));
        assertEquals(2,msgs.size());

        msgs = getAllMessages(session.createConsumer(targetQueue));
        assertEquals(1,msgs.size());
        assertEquals("theOne",msgs.get(0).getText());
    }

    @Test
    public void testSendMapMessage() throws Exception {
        File folder = tempFolder.newFolder();
        final String msgInJson = "{\"TYPE\":\"test\", \"ID\":1}";
        final File file = new File(folder, "file1.json");
        FileUtils.writeStringToFile(file, msgInJson, StandardCharsets.UTF_8);

        final String cmdLine = getConnectCommand() + "-" + CMD_PUT + "@" + file.getAbsolutePath() + " -" + CMD_TYPE + " " + TYPE_MAP + " TEST.QUEUE";
        a.run(cmdLine.split(" "));

        MessageConsumer mc = session.createConsumer(testQueue);
        MapMessage msg1 = (MapMessage)mc.receive(TEST_TIMEOUT);
        assertEquals(msg1.getString("TYPE"), "test");
        assertEquals(msg1.getInt("ID"), 1);
    }

    @Test
    public void testReadFolder() throws Exception {
    	File folder = tempFolder.newFolder();
    	final String file1 = "file1-content";
    	final String file2 = "file2-content";
    	final String file3 = "no-go";
    	FileUtils.writeStringToFile(new File(folder, "file1.txt"), file1, StandardCharsets.UTF_8);
    	FileUtils.writeStringToFile(new File(folder, "file2.txt"), file2, StandardCharsets.UTF_8);
    	FileUtils.writeStringToFile(new File(folder, "file3.dat"), file3, StandardCharsets.UTF_8);
    	Thread.sleep(2000L); // Saturate file age
    	final String fileFilter = folder.getAbsolutePath() + "/*.txt";
    	final String cmdLine = getConnectCommand() + "-" + CMD_READ_FOLDER + " " + fileFilter + " TEST.QUEUE";
    	a.run(cmdLine.split(" "));
    	
    	MessageConsumer mc = session.createConsumer(testQueue);
    	TextMessage msg1 = (TextMessage)mc.receive(TEST_TIMEOUT);
    	assertNotNull(msg1);
    	assertFalse(file3.equals(msg1.getText()));
    	TextMessage msg2 = (TextMessage)mc.receive(TEST_TIMEOUT);
    	assertNotNull(msg2);
    	assertFalse(file3.equals(msg2.getText()));
    	assertNull(mc.receive(TEST_TIMEOUT));
    	File[] remainingFiles = folder.listFiles();
    	assertEquals(1,remainingFiles.length); // one file left - the .dat one
    	assertEquals("file3.dat",remainingFiles[0].getName());
    }

    /**
     * Needed to split command line arguments by space, but not quoted.
     * @param cmdLine command line
     * @return the arguments.
     */
    protected String[] splitCmdLine(String cmdLine){
        List<String> matchList = new ArrayList<String>();
        Pattern regex = Pattern.compile("[^\\s\"]+|\"([^\"]*)\"");
        Matcher regexMatcher = regex.matcher(cmdLine);
        while (regexMatcher.find()) {
            if (regexMatcher.group(1) != null) {
                matchList.add(regexMatcher.group(1));
            } else {
                matchList.add(regexMatcher.group());
            }
        }
        return matchList.toArray(new String[0]);
    }

    protected List<TextMessage> getAllMessages(MessageConsumer mc) throws JMSException {
        TextMessage msg = null;
        List<TextMessage> msgs = new ArrayList<TextMessage>();
        while( (msg = (TextMessage) mc.receive(TEST_TIMEOUT))!=null){
            msgs.add(msg);
        }
        return msgs;
    }
}
