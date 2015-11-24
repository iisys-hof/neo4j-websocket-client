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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.DeploymentException;
import javax.websocket.Session;

import de.hofuniversity.iisys.neo4j.websock.queries.BasicQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.queries.IQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.util.ConnectionWatchdog;
import de.hofuniversity.iisys.neo4j.websock.util.HashUtil;
import de.hofuniversity.iisys.neo4j.websock.util.PingWatchdog;

/**
 * Utility class for connecting to a remote websocket and starting a query
 * handler for the connection.
 */
public class WebSocketConnector
{
    private final String fUri;

    private final Logger fLogger;

    private final String fFormat, fCompression;

    private ConnectionWatchdog fConnWatchdog;
    private BasicQueryHandler fQueryHandler;

    private boolean fFailOnError = false;
    private boolean fWatchdogEnabled = true;

    private PingWatchdog fPingWatchdog;

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
     * during initially creating connections. If this is false, connecting
     * will not cause an Exception to be thrown and the connector will keep
     * on trying to connect.
     *
     * @return whether the connector will stop connecting if an error occurs
     */
    public boolean isFailOnError()
    {
        return fFailOnError;
    }

    /**
     * Sets whether the connector will stop connecting if an error occurs
     * during initially creating connections. If this is false, connecting
     * will not cause an Exception to be thrown and the connector will keep
     * on trying to connect.
     *
     * @param failOnError
     *      whether the connector will stop connecting if an error occurs
     */
    public void setFailOnError(boolean failOnError)
    {
        fFailOnError = failOnError;
    }

    /**
     * Connects to a remote server, creating a query handler and returns the
     * registered client websocket.
     *
     * @return client websocket
     * @throws DeploymentException if connecting fails
     * @throws IOException if communication fails
     */
    public ClientWebSocket connect() throws DeploymentException, IOException
    {
        return connect(null, null, false);
    }

    /**
     * Connects to a remote server, creating a query handler, handles initial
     * authentication and returns the registered client websocket.
     *
     * @param user name of the user
     * @param password the user's password
     * @param hash whether the password is already hashed
     * @return client websocket
     * @throws DeploymentException if connecting fails
     * @throws IOException if communication fails
     */
    public ClientWebSocket connect(String user, char[] password, boolean hash)
        throws DeploymentException, IOException
    {
        //hash password if unhashed
        if(!hash && user != null && password != null)
        {
            password = new HashUtil().hash(user, password).toCharArray();
        }

        if(fConnWatchdog == null
            || fConnWatchdog.getWebsocket() == null)
        {
            fLogger.log(Level.INFO, "connecting to " + fUri);

            //create handlers
            //TODO: configure timeouts etc.
            fQueryHandler = new BasicQueryHandler();

            //connect
            fConnWatchdog = new ConnectionWatchdog(fUri, fQueryHandler,
                fFormat, fCompression);

            if(user != null && password != null)
            {
                fConnWatchdog.setAuthData(user, new String(password));
            }

            try
            {
                fConnWatchdog.connect();
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

            //activate query handler
            Thread queryHandlerThread = new Thread(fQueryHandler);
            queryHandlerThread.start();

            //start watchdogs
            Thread connectionThread = new Thread(fConnWatchdog);
            connectionThread.start();

            if(fWatchdogEnabled)
            {
                fPingWatchdog = new PingWatchdog(fQueryHandler);
                Thread watchdogThread = new Thread(fPingWatchdog);
                watchdogThread.start();
            }
        }

        return fConnWatchdog.getWebsocket();
    }

    /**
     * @return websocket session object
     */
    public Session getSession()
    {
        return fConnWatchdog.getSession();
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
        return fConnWatchdog.getWebsocket();
    }

    /**
     * Stops the query handler and disconnects from the server.
     *
     * @throws IOException if communications fail
     */
    public void disconnect() throws IOException
    {
        if(fConnWatchdog != null)
        {
            fConnWatchdog.disconnect();
        }

        if(fPingWatchdog != null)
        {
            fPingWatchdog.deactivate();
        }

        fQueryHandler.deactivate();
    }
}
