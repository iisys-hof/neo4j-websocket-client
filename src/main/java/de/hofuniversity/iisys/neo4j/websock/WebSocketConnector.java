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

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import de.hofuniversity.iisys.neo4j.websock.queries.BasicQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.queries.IQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.session.WebsockSession;

/**
 * Utility class for connecting to a remote websocket and starting a query
 * handler for the connection.
 */
public class WebSocketConnector
{
    private final String fUri;

    private final Logger fLogger;

    private final String fFormat, fCompression;

    private Session fSession;
    private ClientWebSocket fSocket;
    private BasicQueryHandler fQueryHandler;
    private WebsockSession fWsSess;

    /**
     * Creates a websocket connector that will connect to the given URI,
     * sending and receiving using the given default format.
     * The given URI may not be null or empty.
     *
     * @param uri URI to connect to
     * @param format format to send in
     * @param comp compression method
     */
    public WebSocketConnector(String uri, String format, String comp)
    {
        if(uri == null || uri.isEmpty())
        {
            throw new RuntimeException("websocket URI was null or empty");
        }

        fUri = uri;

        fFormat = format;
        fCompression = comp;

        fLogger = Logger.getLogger(this.getClass().getName());
    }

    public ClientWebSocket connect() throws DeploymentException, IOException
    {
        if(fSession == null)
        {
            fLogger.log(Level.INFO, "connecting to " + fUri);

            //connect
            WebSocketContainer container =
                ContainerProvider.getWebSocketContainer();

            fSocket = new ClientWebSocket();
            fSession = container.connectToServer(fSocket, URI.create(fUri));
            fWsSess = new WebsockSession(fSession);

            //create handlers
            //TODO: configure timeouts etc.
            fQueryHandler = new BasicQueryHandler();
            ServerResponseHandler rHandler = new ServerResponseHandler(fWsSess,
                fQueryHandler, fFormat, fCompression);
            fQueryHandler.setTransferUtil(rHandler.getTransferUtil());


            Thread queryHandlerThread = new Thread(fQueryHandler);
            queryHandlerThread.start();
        }

        return fSocket;
    }

    /**
     * @return websocket session object
     */
    public Session getSession()
    {
        return fSession;
    }

    /**
     * @return registered query handler
     */
    public IQueryHandler getQueryHandler()
    {
        return fQueryHandler;
    }

    /**
     * @return connected websocket instance
     */
    public ClientWebSocket getWebsocket()
    {
        return fSocket;
    }

    /**
     * Stops the query handler and disconnects from the server.
     *
     * @throws IOException if communications fail
     */
    public void disconnect() throws IOException
    {
        if(fSession != null)
        {
            fSession.close();
            fQueryHandler.deactivate();
        }

        fSession = null;
        fSocket = null;
    }
}
