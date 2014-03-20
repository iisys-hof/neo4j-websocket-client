/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package de.hofuniversity.iisys.neo4j.websock;

import javax.websocket.Session;

import org.junit.Assert;

import org.junit.Test;

import de.hofuniversity.iisys.neo4j.websock.queries.IMessageCallback;
import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.session.WebsockConstants;

/**
 * Simple connection test for the client websocket connector.
 */
public class WebsocketConnectorTest
{
    public static final String WS_PATH = "/ws";

    private static final String SERVER_IP = "127.0.0.1";
    private static final String SERVER_PATH = "/junit-websocket";
    private static final int SERVER_PORT = 65210;

    private static final String TEST_MESSAGE = "test message";
    private static final String TEST_RESPONSE = "test message 0";

    /**
     * Tests connecting to a websocket and disconnecting.
     */
    @Test
    public void connectionTest() throws Exception
    {
        //start server
        TestWebsocketServer server = new TestWebsocketServer(true);
        server.start(SERVER_IP, SERVER_PORT, SERVER_PATH);

        //connect
        WebSocketConnector conn = new WebSocketConnector("ws://" + SERVER_IP
            + ":" + SERVER_PORT + SERVER_PATH + WS_PATH,
            WebsockConstants.JSON_FORMAT, WebsockConstants.NO_COMPRESSION);
        conn.setWatchdogEnabled(false);

        conn.connect();
        Session session = conn.getSession();
        Assert.assertTrue(session.isOpen());

        //send and receive message
        WebsockQuery reqQuery = new WebsockQuery(EQueryType.PING);
        reqQuery.setPayload(TEST_MESSAGE);

        IMessageCallback callback = conn.getQueryHandler().sendMessage(reqQuery);

        WebsockQuery response = callback.get();
        Assert.assertEquals(TEST_RESPONSE, response.getPayload());

        //disconnect
        conn.disconnect();
        Assert.assertFalse(session.isOpen());
        Assert.assertFalse(server.isConnected());

        //stop server
        server.stop();

        TestWebsocketServer.INSTANCES.clear();
    }
}
