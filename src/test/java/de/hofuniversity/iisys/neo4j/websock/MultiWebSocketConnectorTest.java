/*
 *  Copyright 2015 Institute of Information Systems, Hof University
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package de.hofuniversity.iisys.neo4j.websock;

import java.util.ArrayList;
import java.util.List;

import javax.websocket.Session;

import org.junit.Assert;
import org.junit.Test;

import de.hofuniversity.iisys.neo4j.websock.queries.IMessageCallback;
import de.hofuniversity.iisys.neo4j.websock.queries.IQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.session.WebsockConstants;

/**
 * Simple connection test for the client websocket connector for multiple connections.
 */
public class MultiWebSocketConnectorTest
{
    //TODO: send and receive test
    //TODO: exceptions?

    private static final String SERVER_1_IP = "127.0.0.1";
    private static final String SERVER_1_PATH = "/junit-websocket1";
    private static final String WS_1_PATH = "/ws";
    private static final int SERVER_1_PORT = 65211;

    private static final String SERVER_2_IP = "127.0.0.1";
    private static final String SERVER_2_PATH = "/junit-websocket2";
    private static final String WS_2_PATH = "/ws";
    private static final int SERVER_2_PORT = 65212;

    private static final String TEST_MESSAGE = "test message";

    /**
     * Tests connecting to a websocket and disconnecting.
     */
    @Test
    public void connectionTest() throws Exception
    {
        //start server 1
        TestWebsocketServer server1 = new TestWebsocketServer(true);
        server1.start(SERVER_1_IP, SERVER_1_PORT, SERVER_1_PATH);

        //start server 2
        TestWebsocketServer server2 = new TestWebsocketServer(true);
        server2.start(SERVER_2_IP, SERVER_2_PORT, SERVER_2_PATH);

        //connect
        List<String> uris = new ArrayList<String>();
        uris.add("ws://" + SERVER_1_IP + ":" + SERVER_1_PORT + SERVER_1_PATH + WS_1_PATH);
        uris.add("ws://" + SERVER_2_IP + ":" + SERVER_2_PORT + SERVER_2_PATH + WS_2_PATH);

        MultiWebSocketConnector conn = new MultiWebSocketConnector(uris, 2,
            WebsockConstants.JSON_FORMAT, WebsockConstants.NO_COMPRESSION);
        conn.setWatchdogEnabled(false);

        conn.connect();
        List<Session> sessions = conn.getSessions();
        Assert.assertEquals(4, sessions.size());

        Assert.assertEquals(4, TestWebsocketServer.INSTANCES.size());
        for(TestWebsocketServer server : TestWebsocketServer.INSTANCES)
        {
            Assert.assertTrue(server.isConnected());
        }

        for(Session session : sessions)
        {
            Assert.assertTrue(session.isOpen());
        }

        //TODO: send and receive messages

        //send message to all servers
        IQueryHandler qHandler = conn.getQueryHandler();

        WebsockQuery reqQuery = new WebsockQuery(EQueryType.PING);
        reqQuery.setPayload(TEST_MESSAGE);

        IMessageCallback mCb = qHandler.sendMessage(reqQuery);
        mCb.get();

        for(TestWebsocketServer server : TestWebsocketServer.INSTANCES)
        {
            Assert.assertEquals(1, server.getMessageCount());
        }


        //send messages to single servers (round robin)
        reqQuery = new WebsockQuery(EQueryType.PROCEDURE_CALL);
        reqQuery.setPayload(TEST_MESSAGE);

        //server 0
        mCb = qHandler.sendMessage(reqQuery);
        WebsockQuery response = mCb.get();
        Assert.assertEquals(TEST_MESSAGE + " 0", response.getPayload());

        //server 1
        mCb = qHandler.sendMessage(reqQuery);
        response = mCb.get();
        Assert.assertEquals(TEST_MESSAGE + " 1", response.getPayload());

        //server 2
        mCb = qHandler.sendMessage(reqQuery);
        response = mCb.get();
        Assert.assertEquals(TEST_MESSAGE + " 2", response.getPayload());

        //server 3
        mCb = qHandler.sendMessage(reqQuery);
        response = mCb.get();
        Assert.assertEquals(TEST_MESSAGE + " 3", response.getPayload());

        for(TestWebsocketServer server : TestWebsocketServer.INSTANCES)
        {
            Assert.assertEquals(2, server.getMessageCount());
        }

        //disconnect
        conn.disconnect();

        for(Session session : sessions)
        {
            Assert.assertFalse(session.isOpen());
        }

        for(TestWebsocketServer server : TestWebsocketServer.INSTANCES)
        {
            Assert.assertFalse(server.isConnected());
        }

        //stop servers
        server1.stop();
        server2.stop();

        TestWebsocketServer.INSTANCES.clear();
    }
}
