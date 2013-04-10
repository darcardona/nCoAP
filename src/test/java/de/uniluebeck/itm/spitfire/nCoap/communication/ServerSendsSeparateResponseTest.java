package de.uniluebeck.itm.spitfire.nCoap.communication;

import de.uniluebeck.itm.spitfire.nCoap.communication.core.CoapClientDatagramChannelFactory;
import de.uniluebeck.itm.spitfire.nCoap.communication.utils.CoapMessageReceiver;
import de.uniluebeck.itm.spitfire.nCoap.communication.utils.CoapTestClient;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapMessage;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapRequest;
import de.uniluebeck.itm.spitfire.nCoap.message.CoapResponse;
import de.uniluebeck.itm.spitfire.nCoap.message.header.Code;
import de.uniluebeck.itm.spitfire.nCoap.message.header.MsgType;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.SortedMap;

import static junit.framework.Assert.*;
import static de.uniluebeck.itm.spitfire.nCoap.testtools.ByteTestTools.*;


/**
 * Tests to verify the client functionality related to separate responses.
 *
 * @author Stefan Hueske
 */
public class ServerSendsSeparateResponseTest {

    private static CoapTestClient testClient= CoapTestClient.getInstance();
    private static CoapMessageReceiver testReceiver = CoapMessageReceiver.getInstance();

    //request
    private static URI targetUri;
    private static CoapRequest coapRequest;
    private static String requestPath;

    //empty ACK
    private static CoapResponse emptyACK;

    //response
    private static CoapResponse coapResponse;
    private static String responsePayload;
    private static int responseMsgID;


    @BeforeClass
    public static void init() throws Exception {
        //init
        testClient.reset();
        testReceiver.reset();
        testReceiver.setReceiveEnabled(true);
        testReceiver.setWriteEnabled(false);

        //create request
        requestPath = "/testpath";
        targetUri = new URI("coap://localhost:" + CoapMessageReceiver.RECEIVER_PORT + requestPath);
        coapRequest = new CoapRequest(MsgType.CON, Code.GET, targetUri, testClient);

        //create empy ack
        emptyACK = new CoapResponse(Code.EMPTY);
        emptyACK.getHeader().setMsgType(MsgType.ACK);

        //create response
        responsePayload = "testpayload";
        coapResponse = new CoapResponse(Code.CONTENT_205);
        coapResponse.setPayload(responsePayload.getBytes("UTF-8"));
        coapResponse.getHeader().setMsgType(MsgType.CON);

        //write request, disable receiving after 500ms
        testClient.writeCoapRequest(coapRequest);
        Thread.sleep(1000);

        //send empty ack
        emptyACK.setMessageID(coapRequest.getMessageID());
        testReceiver.writeMessage(emptyACK, new InetSocketAddress("localhost",
                CoapClientDatagramChannelFactory.COAP_CLIENT_PORT));
        Thread.sleep(1000);

        //send separate response
        responseMsgID = 3333;
        coapResponse.setToken(coapRequest.getToken());
        coapResponse.setMessageID(responseMsgID);
        testReceiver.writeMessage(coapResponse, new InetSocketAddress("localhost",
                CoapClientDatagramChannelFactory.COAP_CLIENT_PORT));
        //wait for ack
        Thread.sleep(300);
        testReceiver.setReceiveEnabled(false);
    }

    @Test
    public void testReceivedRequestEqualsSentRequest() {
        SortedMap<Long, CoapMessage> receivedRequests = testReceiver.getReceivedMessages();
        String message = "Written and received request do not equal";
        assertEquals(message, coapRequest, receivedRequests.get(receivedRequests.firstKey()));
    }

    @Test
    public void testReceiverReceivedTwoMessages() {
        String message = "Receiver received more than one message";
        assertEquals(message, 2, testReceiver.getReceivedMessages().values().size());
    }

    @Test
    public void testClientCallbackInvokedOnce() {
        String message = "Client callback was invoked less or more than once";
        assertEquals(message, 1, testClient.getReceivedResponses().values().size());
    }

    @Test
    public void test2ndReceivedMessageIsEmpyACK() {
        SortedMap<Long, CoapMessage> receivedMessages = testReceiver.getReceivedMessages();
        CoapMessage receivedMessage = receivedMessages.get(receivedMessages.lastKey());
        String message = "First received message is not an EMPTY ACK";
        assertEquals(message, Code.EMPTY, receivedMessage.getCode());
        assertEquals(message, MsgType.ACK, receivedMessage.getMessageType());
    }
}
