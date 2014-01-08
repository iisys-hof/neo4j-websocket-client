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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import de.hofuniversity.iisys.neo4j.websock.queries.IQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.queries.MultiConnQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.TransferUtil;
import de.hofuniversity.iisys.neo4j.websock.session.WebsockSession;

/**
 * Utility class for connecting to a remote websocket and starting a query
 * handler for the connection.
 */
public class MultiWebSocketConnector
{
    private final List<String> fUris;
    private final int fConnCount;

    private final String fFormat, fCompression;

    private final Logger fLogger;

    private final List<Session> fSessions;
    private final List<WebsockSession> fWsSessions;

    private ClientWebSocket fSocket;

    private MultiConnQueryHandler fQueryHandler;

    /**
     * Creates a websocket connector that will connect to the given URIs,
     * sending and receiving using the given default format.
     * The given URI may not be null or empty.
     *
     * @param uris URIs to connect to
     * @param conns connections per URL
     * @param format format to send in
     * @param comp compression method
     */
    public MultiWebSocketConnector(List<String> uris, int conns, String format,
        String comp)
    {
        if(uris == null || uris.isEmpty())
        {
            throw new RuntimeException("websocket URI list was null or empty");
        }

        fUris = uris;
        fConnCount = conns;
        fSessions = new ArrayList<Session>();
        fWsSessions = new ArrayList<WebsockSession>();

        fFormat = format;
        fCompression = comp;

        fLogger = Logger.getLogger(this.getClass().getName());
    }

    public ClientWebSocket connect() throws DeploymentException, IOException
    {
        Session session = null;
        for(String uri : fUris)
        {
            for(int i = 0; i < fConnCount; ++i)
            {
                fLogger.log(Level.INFO, "connecting (" + i + ") to " + uri);

                //connect
                WebSocketContainer container =
                    ContainerProvider.getWebSocketContainer();

                fSocket = new ClientWebSocket();
                session = container.connectToServer(fSocket, URI.create(uri));

                fSessions.add(session);
                fWsSessions.add(new WebsockSession(session));
            }
        }

        //create handlers
        final List<TransferUtil> tUtils = new ArrayList<TransferUtil>();
        fQueryHandler = new MultiConnQueryHandler();

        ServerResponseHandler rHandler = null;
        for(WebsockSession wsSess : fWsSessions)
        {
            rHandler = new ServerResponseHandler(wsSess, fQueryHandler,
                fFormat, fCompression);
            tUtils.add(rHandler.getTransferUtil());
        }

        fQueryHandler.setTransferUtils(tUtils);

        Thread queryHandlerThread = new Thread(fQueryHandler);
        queryHandlerThread.start();

        return fSocket;
    }

    /**
     * @return list of websocket session objects
     */
    public List<Session> getSessions()
    {
        return fSessions;
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
     * Stops the query handler and disconnects from the servers.
     *
     * @throws IOException if communications fail
     */
    public void disconnect() throws IOException
    {
        for(Session session : fSessions)
        {
            session.close();
        }

        fQueryHandler.deactivate();
    }
}
