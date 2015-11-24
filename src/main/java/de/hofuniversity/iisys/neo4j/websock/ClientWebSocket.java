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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import de.hofuniversity.iisys.neo4j.websock.util.ConnectionWatchdog;

/**
 * Annotated client endpoint implementation logging connections and errors.
 */
@ClientEndpoint
public class ClientWebSocket
{
    private final Logger fLogger;

    private ConnectionWatchdog fWatchdog;
    private Session fSession;

    /**
     * Creates a logging client websocket.
     */
    public ClientWebSocket()
    {
        fLogger = Logger.getLogger(this.getClass().getName());
    }

    /**
     * Registers new sessions.
     *
     * @param session session opened
     * @param config configuration received
     */
    @OnOpen
    public void onOpen(Session session, EndpointConfig config)
    {
        fSession = session;
        fLogger.log(Level.INFO, "websocket opened");
    }

    /**
     * @param watchdog watchdog to notify on closing
     */
    public void setWatchdog(ConnectionWatchdog watchdog)
    {
        fWatchdog = watchdog;
    }

    /**
     * Unregisters closed sessions, notifies the watchdog and logs the closing
     * reason.
     *
     * @param session session closed
     * @param closeReason reason for closing
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason)
    {
        fSession = null;
        fLogger.log(Level.INFO, closeReason.getReasonPhrase());

        fWatchdog.disconnected();
    }

    /**
     * Logs incoming errors.
     *
     * @param session session in which the error occurred
     * @param throwable exception that was thrown
     */
    @OnError
    public void onError(Session session, Throwable throwable)
    {
        throwable.printStackTrace();
        fLogger.log(Level.SEVERE, throwable.getMessage(), throwable);
    }

    /**
     * @return websocket session object
     */
    public Session getSession()
    {
        return fSession;
    }
}
