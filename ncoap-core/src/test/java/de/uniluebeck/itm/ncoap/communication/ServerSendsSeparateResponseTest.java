/**
 * Copyright (c) 2012, Oliver Kleine, Institute of Telematics, University of Luebeck
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *  - Redistributions of source messageCode must retain the above copyright notice, this list of conditions and the following
 *    disclaimer.
 *
 *  - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *  - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or promote
 *    products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.uniluebeck.itm.ncoap.communication;

import de.uniluebeck.itm.ncoap.application.client.CoapClientApplication;
import de.uniluebeck.itm.ncoap.communication.dispatching.client.Token;
import de.uniluebeck.itm.ncoap.endpoints.DummyEndpoint;
import de.uniluebeck.itm.ncoap.endpoints.client.ClientTestCallback;
import de.uniluebeck.itm.ncoap.message.*;
import de.uniluebeck.itm.ncoap.message.MessageCode;

import java.net.InetSocketAddress;

import de.uniluebeck.itm.ncoap.message.options.ContentFormat;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

import java.net.URI;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;


/**
* Tests to verify the client functionality related to separate responses.
*
* @author Stefan Hueske, Oliver Kleine
*/
public class ServerSendsSeparateResponseTest extends AbstractCoapCommunicationTest {

    private static CoapClientApplication client;
    private static ClientTestCallback clientCallback;
    private static CoapRequest request;

    private static DummyEndpoint endpoint;
    private static InetSocketAddress endpointSocket;


    @Override
    public void setupLogging() throws Exception {
        Logger.getLogger("de.uniluebeck.itm.ncoap.communication.reliability.client").setLevel(Level.DEBUG);
        Logger.getLogger("de.uniluebeck.itm.ncoap.plugtest.endpoint.DummyEndpoint").setLevel(Level.DEBUG);
    }

    @Override
    public void setupComponents() throws Exception {
        endpoint = new DummyEndpoint();
        endpointSocket = new InetSocketAddress("localhost", endpoint.getPort());

        client = new CoapClientApplication();
        clientCallback = new ClientTestCallback();

        URI targetUri = new URI("coap", null, "localhost", endpoint.getPort(), "/service/path", null, null);
        request = new CoapRequest(MessageType.Name.CON, MessageCode.Name.GET, targetUri);
    }

    @Override
    public void shutdownComponents() throws Exception {
        client.shutdown();
        endpoint.shutdown();
    }


    @Override
    public void createTestScenario() throws Exception {


//             testClient                    testEndpoint     DESCRIPTION
//                  |                             |
//              (1) |--------GET----------------->|           testClient sends request to testEndpoint
//                  |                             |
//              (2) |<-------EMPTY-ACK------------|           testEndpoint responds with empty ack to indicate a separate response
//                  |                             | |
//                  |                             | | wait 1 second to simulate processing time
//                  |                             | |
//              (3) |<-------CON-RESPONSE---------|           testEndpoint sends separate response
//                  |                             |
//              (4) |--------EMPTY-ACK----------->|           testClient confirms arrival
//                  |                             |
//                  |                             |


        //write request
        client.sendCoapRequest(request, clientCallback, endpointSocket);

        //wait (2000 - epsilon) milliseconds
        Thread.sleep(1800);

        //create and write empty ACK
        int messageID = endpoint.getReceivedCoapMessages().values().iterator().next().getMessageID();
        CoapMessage emptyACK = CoapMessage.createEmptyAcknowledgement(messageID);
        endpoint.writeMessage(emptyACK, new InetSocketAddress("localhost", client.getPort()));

        //wait another some time to simulate request processing
        Thread.sleep(2000);

        //create seperate response to be sent by the message receiver
        Token token = endpoint.getReceivedCoapMessages().values().iterator().next().getToken();

        CoapResponse seperateResponse = new CoapResponse(MessageType.Name.CON, MessageCode.Name.CONTENT_205);
        seperateResponse.setMessageID(11111);
        seperateResponse.setToken(token);
        seperateResponse.setContent("Some payload...".getBytes(CoapMessage.CHARSET), ContentFormat.TEXT_PLAIN_UTF8);

        //send seperate response
        endpoint.writeMessage(seperateResponse, new InetSocketAddress("localhost", client.getPort()));

        //wait some time for ACK from client
        Thread.sleep(500);
    }



    @Test
    public void testReceivedRequestEqualsSentRequest() {
        SortedMap<Long, CoapMessage> receivedRequests = endpoint.getReceivedCoapMessages();
        String message = "Written and received request do not equal";
        assertEquals(message, request, receivedRequests.get(receivedRequests.firstKey()));
    }

    @Test
    public void testEndpointReceivedTwoMessages() {
        String message = "Receiver received wrong number of messages";
        assertEquals(message, 2, endpoint.getReceivedCoapMessages().values().size());
    }

    @Test
    public void testClientCallbackInvokedOnce() {
        String message = "Client received wrong number of responses!";
        assertEquals(message, 1, clientCallback.getCoapResponses().size());
    }

    @Test
    public void testClientReceivedEmptyAck(){
        assertEquals("Wrong number of empty ACKs!", 1, clientCallback.getEmptyACKs().size());
    }

    @Test
    public void testServerReceivedEmptyACK() {
        SortedMap<Long, CoapMessage> receivedMessages = endpoint.getReceivedCoapMessages();
        CoapMessage receivedMessage = receivedMessages.get(receivedMessages.lastKey());
        String message = "Second received message is not an EMPTY ACK";
        assertEquals(message, MessageCode.Name.EMPTY, receivedMessage.getMessageCodeName());
        assertEquals(message, MessageType.Name.ACK, receivedMessage.getMessageTypeName());
    }
}
