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
package de.hofuniversity.iisys.neo4j.websock.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import de.hofuniversity.iisys.neo4j.websock.queries.IMessageCallback;
import de.hofuniversity.iisys.neo4j.websock.queries.IQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;

/**
 * Watchdog sending a ping query to a remote server in configurable intervals
 * to keep the connection alive and to check, whether connections are still
 * open.
 */
public class PingWatchdog implements Runnable
{
    private static final long DEFAULT_INTERVAL = 10000;

    private final Object fTrigger;
    private final Logger fLogger;

    private final IQueryHandler fHandler;
    private final long fInterval;

    private boolean fActive;

    /**
     * Creates a new server pinging watchdog with a default interval, sending
     * pings using the given query handler.
     * Throws a NullPointerException if the given handler is null.
     *
     * @param handler handler to use for sending pings
     */
    public PingWatchdog(IQueryHandler handler)
    {
        this(handler, DEFAULT_INTERVAL);
    }

    /**
     * Creates a new server pinging watchdog with the given interval, sending
     * pings using the given query handler.
     * Throws a NullPointerException if the given handler is null.
     *
     * @param handler handler to use for sending pings
     * @param interval millisecond interval between pings
     */
    public PingWatchdog(IQueryHandler handler, long interval)
    {
        fLogger = Logger.getLogger(this.getClass().getName());

        if(handler == null)
        {
            throw new NullPointerException("query handler was null");
        }
        if(interval <= 0)
        {
            throw new IllegalArgumentException(
                "interval was negative or zero");
        }

        fHandler = handler;
        fInterval = interval;
        fTrigger = new Object();
    }

    /**
     * Deactivates the watchdog.
     */
    public void deactivate()
    {
        fActive = false;
        synchronized(fTrigger)
        {
            fTrigger.notify();
        }
    }

    @Override
    public void run()
    {
        fActive = true;

        while(fActive)
        {
            //send ping
            WebsockQuery message = new WebsockQuery(EQueryType.PING);
            IMessageCallback cb = null;
            WebsockQuery response = null;

            try
            {
                cb = fHandler.sendMessage(message);
                response = cb.get();
            }
            catch(Exception e)
            {
                fLogger.log(Level.SEVERE, "ping watchdog failed to send ping",
                    e);
            }

            //check whether ping was successful
            if(cb.isCancelled())
            {
                fLogger.log(Level.SEVERE, "watchdog ping query cancelled");
            }
            else if(response == null)
            {
                fLogger.log(Level.SEVERE, "watchdog ping query cancelled");
            }

            if(cb.getErrorMessage() != null)
            {
                fLogger.log(Level.SEVERE, "ping watchdog error: "
                    + cb.getErrorMessage());
            }

            //wait for a certain time
            try
            {
                if(fActive)
                {
                    synchronized(fTrigger)
                    {
                        fTrigger.wait(fInterval);
                    }
                }
            }
            catch(Exception e)
            {
                fLogger.log(Level.WARNING, "ping watchdog interrupted", e);
                fActive = false;
            }
        }
    }

}
