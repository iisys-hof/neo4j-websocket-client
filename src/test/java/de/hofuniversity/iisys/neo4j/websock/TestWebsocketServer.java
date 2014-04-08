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

import java.util.HashSet;
import java.util.Set;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.server.Server;
import org.junit.Assert;

import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.safe.TSafeJsonQueryHandler;

/**
 * Minimal websocket server remembering instances, countining messages and
 * connections, receiving JSON messages and responding with JSON messages with
 * its server number added to the payload.
 */
@ServerEndpoint(value = WebsocketConnectorTest.WS_PATH)
public class TestWebsocketServer
{
    /**
     * Set of instances created using the default constructor.
     */
    public static final Set<TestWebsocketServer> INSTANCES =
        new HashSet<TestWebsocketServer>();

    private final int fServNum;

    private Server fServer;

    private boolean fConnected;
    private int fConnectionCount, fMessageCount;

    /**
     * Default constructor, adding the created instance to the set of
     * instances.
     */
    public TestWebsocketServer()
    {
        fServNum = INSTANCES.size();
        INSTANCES.add(this);
    }

    /**
     * Creates an instance that can be used for server startup, but is not
     * linked to an actual websocket.
     *
     * @param startup
     *      parameter to differentiate this constructor from the default
     *      constructor
     */
    public TestWebsocketServer(boolean startup)
    {
        //no-op
        fServNum = 0;
    }

    /**
     * Starts a websocket server with for given host, with the given port and
     * path.
     * Parameters must not be null.
     *
     * @param host host to start the server for
     * @param port port to start the server on
     * @param path context path for the websocket deployment
     * @throws Exception if startup fails
     */
    public void start(String host, int port, String path) throws Exception
    {
        fServer = new Server(host, port, path, null,
            TestWebsocketServer.class);

        fServer.start();
    }

    /**
     * Stops the server handled by this instance or throws an Exception if that
     * is not possible.
     *
     * @throws Exception if the server's shutdown fails
     */
    public void stop() throws Exception
    {
        fServer.stop();
    }

    /**
     * Called when a websocket connection is opened by a client.
     * Sets the connected flag to true and increases the number of active
     * connections.
     *
     * @param session newly opened session
     * @param config configuration object
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config)
    {
        fConnected = true;
        ++fConnectionCount;
    }

    /**
     * Called when a websocket connection is closed.
     * Sets the connected flag to false and decreases the number of active
     * connections.
     *
     * @param session closed session
     * @param closeReason reason why the session was closed
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason)
    {
        fConnected = false;
        --fConnectionCount;
    }

    /**
     * @return whether the 'connected' flag is set
     */
    public boolean isConnected()
    {
        return fConnected;
    }

    /**
     * @return how many connections were opened to this instance
     */
    public int getConnectionCount()
    {
        return fConnectionCount;
    }

    /**
     * @return how many messages were received by this instance
     */
    public int getMessageCount()
    {
        return fMessageCount;
    }

    /**
     * Called when a text message is received, expecting a JSON query.
     * Increases the 'received messages' counter and replies with a modified payload.
     *
     * @param session session the message originated from
     * @param message text message that was sent
     */
    @OnMessage
    public void onMessage(Session session, String message)
    {
        final TSafeJsonQueryHandler handler = new TSafeJsonQueryHandler();
        ++fMessageCount;

        try
        {
            WebsockQuery query = handler.decode(message);

            query.setPayload(query.getPayload() + " " + fServNum);

            message = handler.encode(query);
            session.getBasicRemote().sendText(message);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    /**
     * Called when an error occurs.
     * Prints the error's stack trace to the console
     *
     * @param session session in which the error occurred
     * @param t throwable that was thrown
     */
    @OnError
    public void onError(Session session, Throwable t)
    {
        t.printStackTrace();
    }
}