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
package de.hofuniversity.iisys.neo4j.websock.util;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.sasl.AuthenticationException;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import de.hofuniversity.iisys.neo4j.websock.ClientWebSocket;
import de.hofuniversity.iisys.neo4j.websock.ServerResponseHandler;
import de.hofuniversity.iisys.neo4j.websock.queries.IMessageCallback;
import de.hofuniversity.iisys.neo4j.websock.queries.IQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.TransferUtil;
import de.hofuniversity.iisys.neo4j.websock.session.WebsockConstants;
import de.hofuniversity.iisys.neo4j.websock.session.WebsockSession;

/**
 * Watchdog creating a single connection and monitoring its connectivity,
 * reconnecting if a connection is lost and adding a new transfer utility
 * back into the system.
 */
public class ConnectionWatchdog implements Runnable
{
    private static final long CHECK_INTERVAL = 1000;
    private static final long RECONNECT_INTERVAL = 5000;

    private final Object fTrigger;
    private final Logger fLogger;

    private final URI fUri;

    private final IQueryHandler fHandler;

    private final String fFormat, fCompression;

    private ClientWebSocket fSocket;
    private Session fSession;
    private WebsockSession fWsSess;
    private TransferUtil fUtil;

    private String fUser, fPassword;

    private boolean fActive;
    private boolean fDisconnected;

    /**
     * Creates a connection watchdog, establishing and monitoring a single
     * connection to the specified websocket URI.
     * Throws a NullPointerException if any arguments are null or empty.
     *
     * @param uri websocket URI to connect to
     * @param handler query handler to add connections to
     * @param format format to send and receive data in
     * @param comp whether to use compression for transfers
     */
    public ConnectionWatchdog(String uri, IQueryHandler handler,
        String format, String comp)
    {
        fLogger = Logger.getLogger(this.getClass().getName());

        if(uri == null || uri.isEmpty())
        {
            throw new NullPointerException("no URI given");
        }
        if(handler == null)
        {
            throw new NullPointerException("query handler was null");
        }
        if(format == null || format.isEmpty())
        {
            throw new NullPointerException("no transfer format given");
        }
        if(comp == null || comp.isEmpty())
        {
            throw new NullPointerException("no compression parameter given");
        }

        fTrigger = new Object();
        fUri = URI.create(uri);
        fHandler = handler;

        fFormat = format;
        fCompression = comp;
        fDisconnected = false;
    }

    /**
     * Sets the authentication data to transmit after connecting.
     * If any parameter is null, no information will be transmitted.
     * The given password String will be transmitted directly, so it can
     * either be clear text or hashed.
     *
     * @param user user name to transmit
     * @param password password to transmit
     */
    public void setAuthData(String user, String password)
    {
        fUser = user;
        fPassword = password;
    }

    @Override
    public void run()
    {
        fActive = true;

        while(fActive)
        {
            if(fSession == null || !fSession.isOpen()
                || fDisconnected)
            {
                fDisconnected = false;

                fLogger.log(Level.SEVERE, "connection to " + fUri.toString()
                    + " lost, trying to reconnect");

                retryLoop();
            }

            try
            {
                if(fActive)
                {
                    synchronized(fTrigger)
                    {
                        fTrigger.wait(CHECK_INTERVAL);
                    }
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
                fActive = false;
            }
        }
    }

    private void retryLoop()
    {
        do
        {
            try
            {
                connect();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

            try
            {
                if(fActive
                    && (fSession == null || !fSession.isOpen()))
                {
                    synchronized(fTrigger)
                    {
                        fTrigger.wait(RECONNECT_INTERVAL);
                    }
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
                fActive = false;
            }
        } while(fActive
            && (fSession == null || !fSession.isOpen()));

        fDisconnected = false;
    }

    /**
     * Connects to the configured websocket, discarding any old connections.
     *
     * @throws Exception if connecting fails
     */
    public void connect() throws DeploymentException, IOException
    {
        simpleDisconnect();

        fLogger.log(Level.INFO, "connecting to " + fUri);

        //connect
        WebSocketContainer container =
            ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxBinaryMessageBufferSize(Integer.MAX_VALUE);
        container.setDefaultMaxTextMessageBufferSize(Integer.MAX_VALUE);

        fSocket = new ClientWebSocket();
        fSocket.setWatchdog(this);
        fSession = container.connectToServer(fSocket, fUri);
        fWsSess = new WebsockSession(fSession);

        //create response handler
        ServerResponseHandler rHandler = new ServerResponseHandler(fWsSess,
            fHandler, fFormat, fCompression);
        fUtil = rHandler.getTransferUtil();

        //send authentication query if configured
        boolean authenticated = authenticate();
        if(!authenticated)
        {
            simpleDisconnect();
            throw new AuthenticationException(
                "authentication failed, disconnecting");
        }

        //register transfer utility
        fHandler.addTransferUtil(fUtil);
    }

    private boolean authenticate()
    {
        boolean success = true;

        if(fUser != null && fPassword != null)
        {
            WebsockQuery message = new WebsockQuery(EQueryType.AUTHENTICATION);
            message.setParameter(WebsockConstants.USERNAME, fUser);
            message.setParameter(WebsockConstants.PASSWORD, fPassword);

            IMessageCallback callback = fHandler.sendDirectMessage(message,
                fUtil);

            try
            {
                callback.get();
            }
            catch(Exception e)
            {
                String error = "authentication failed";

                if(callback.getErrorMessage() != null)
                {
                    error += ":\n" + callback.getErrorMessage();
                }

                fLogger.log(Level.SEVERE, error, e);
                success = false;
            }
        }

        return success;
    }

    /**
     * @return connection session, if there is one
     */
    public Session getSession()
    {
        return fSession;
    }

    /**
     * @return connected client websocket
     */
    public ClientWebSocket getWebsocket()
    {
        return fSocket;
    }

    /**
     * External notification method to tell the watchdog that the connection
     * has been terminated.
     */
    public void disconnected()
    {
        fDisconnected = true;
        synchronized(fTrigger)
        {
            fTrigger.notify();
        }
    }

    private void simpleDisconnect()
    {
        fHandler.removeTransferUtil(fUtil);

        if(fSession != null && fSession.isOpen())
        {
            try
            {
                fSession.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Disconnects the configured websocket, not restarting the connection.
     */
    public void disconnect()
    {
        fLogger.log(Level.INFO, "disconnecting from " + fUri);

        fActive = false;
        synchronized(fTrigger)
        {
            fTrigger.notify();
        }

        simpleDisconnect();
    }
}
