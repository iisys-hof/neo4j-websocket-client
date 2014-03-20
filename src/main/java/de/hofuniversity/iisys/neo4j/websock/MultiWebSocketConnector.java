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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.DeploymentException;
import javax.websocket.Session;

import de.hofuniversity.iisys.neo4j.websock.queries.IQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.queries.MultiConnQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.util.ConnectionWatchdog;
import de.hofuniversity.iisys.neo4j.websock.util.PingWatchdog;

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

    private final List<ConnectionWatchdog> fConnWatchdogs;

    private ClientWebSocket fSocket;

    private MultiConnQueryHandler fQueryHandler;

    private boolean fFailOnError = false;
    private boolean fWatchdogEnabled = true;

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
        fConnWatchdogs = new ArrayList<ConnectionWatchdog>();

        fFormat = format;
        fCompression = comp;

        fLogger = Logger.getLogger(this.getClass().getName());
    }
    
    /**
     * @return whether the watchdog will be enabled after connecting.
     */
    public boolean isWatchdogEnabled()
    {
        return fWatchdogEnabled;
    }
    
    /**
     * @param enabled whether to enable the watchdog after connecting
     */
    public void setWatchdogEnabled(boolean enabled)
    {
        fWatchdogEnabled = enabled;
    }
    
    /**
     * Returns whether the connector will stop connecting if an error occurs
     * during initially creating connections. It will also stop, if only one
     * connection failsIf this is false, connecting will not cause an Exception
     * to be thrown and the connector will keep on trying to connect.
     * 
     * @return whether the connector will stop connecting if an error occurs
     */
    public boolean isFailOnError()
    {
        return fFailOnError;
    }

    /**
     * Sets whether the connector will stop connecting if an error occurs
     * during initially creating connections. It will also stop, if only one
     * connection failsIf this is false, connecting will not cause an Exception
     * to be thrown and the connector will keep on trying to connect.
     * 
     * @param failOnError
     *      whether the connector will stop connecting if an error occurs
     */
    public void setFailOnError(boolean failOnError)
    {
        fFailOnError = failOnError;
    }

    /**
     * Connects to a number of remote servers with a number of connections,
     * creating a query handler and returns a registered client websocket.
     * 
     * @return client websocket
     * @throws DeploymentException if connecting fails
     * @throws IOException if communication fails
     */
    public ClientWebSocket connect() throws DeploymentException, IOException
    {
        ConnectionWatchdog connWatchdog = null;

        //create handlers
        //TODO: configure timeouts etc.
        fQueryHandler = new MultiConnQueryHandler();
        
        for(String uri : fUris)
        {
            for(int i = 0; i < fConnCount; ++i)
            {
                fLogger.log(Level.INFO, "connecting (" + i + ") to " + uri);

                //connect
                connWatchdog = connectTo(uri);
                
                fConnWatchdogs.add(connWatchdog);
            }
        }

        Thread queryHandlerThread = new Thread(fQueryHandler);
        queryHandlerThread.start();
        
        //start watchdogs
        for(ConnectionWatchdog wd : fConnWatchdogs)
        {
            Thread connectionThread = new Thread(wd);
            connectionThread.start();
        }
        
        if(fWatchdogEnabled)
        {
            PingWatchdog watchdog = new PingWatchdog(fQueryHandler);
            Thread watchdogThread = new Thread(watchdog);
            watchdogThread.start();
        }

        return fSocket;
    }
    
    private ConnectionWatchdog connectTo(String uri)
        throws DeploymentException, IOException
    {
        ConnectionWatchdog connWatchdog = new ConnectionWatchdog(uri, fQueryHandler,
            fFormat, fCompression);
        
        try
        {
            connWatchdog.connect();
        }
        catch(DeploymentException e)
        {
            fLogger.log(Level.SEVERE,
                "error while establishing initial connection", e);
            e.printStackTrace();
            
            //propagate exception if an initial connection is needed
            if(fFailOnError)
            {
                throw e;
            }
        }
        catch(IOException e)
        {
            fLogger.log(Level.SEVERE,
                "error while establishing initial connection", e);
            e.printStackTrace();
            
            //propagate exception if an initial connection is needed
            if(fFailOnError)
            {
                throw e;
            }
        }
        
        return connWatchdog;
    }

    /**
     * @return list of websocket session objects
     */
    public List<Session> getSessions()
    {
        final List<Session> sessions = new ArrayList<Session>();
        
        for(ConnectionWatchdog wd : fConnWatchdogs)
        {
            sessions.add(wd.getSession());
        }
        
        return sessions;
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
        for(ConnectionWatchdog wd : fConnWatchdogs)
        {
            wd.disconnect();
        }

        fQueryHandler.deactivate();
    }
}
