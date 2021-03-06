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
package de.hofuniversity.iisys.neo4j.websock.queries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.TransferUtil;
import de.hofuniversity.iisys.neo4j.websock.result.AResultSet;
import de.hofuniversity.iisys.neo4j.websock.util.ResultSetConverter;

/**
 * Handler for incoming and outgoing messages for multiple connections that
 * handles responses to previously sent messages and queries and handles
 * timeouts and retries.
 * Except for queries, all calls are sent to all servers.
 */
public class MultiConnQueryHandler implements IQueryHandler
{
    public static final long DEFAULT_TIMEOUT_MS = 300000;
    public static final long DEFAULT_TIMER_MS = 1000;
    public static final int DEFAULT_RETRIES = 0;

    public static final int ID_POOLS = 40;
    public static final int ID_POOL_SIZE = 100000;

    private final Object fTrigger;

    private final List<TransferUtil> fSessionPool;

    private final Object[] fPoolIdLocks;
    private final int[] fPoolIds;

    private final Map<Integer, WebsockQuery> fPendingQueries;
    private final Map<Integer, IMessageCallback> fPendingMessages;
    private final Map<Integer, IQueryCallback> fPendingResults;

    private final Map<Integer, Long> fTimeouts;
    private final Map<Integer, Integer> fRetries;
    private final Map<Integer, Integer> fMultiCounters;

    private final Map<String, WebsockQuery> fProcedureQueries;

    private final Logger fLogger;
    private final boolean fDebug;

    private long fTimeout;
    private long fTimerInt;
    private int fRetryNum;

    private boolean fResendProcedures;

    private int fPoolCounter;
    private int fIdPoolCounter;

    private boolean fActive;

    /**
     * Creates a multi-connection query handler with default values that still
     * needs at least one transfer utility to function properly.
     */
    public MultiConnQueryHandler()
    {
        fSessionPool = new ArrayList<TransferUtil>();
        fPoolCounter = 0;

        fPoolIdLocks = new Object[ID_POOLS];
        fPoolIds = new int[ID_POOLS];

        //initialize separated ID pools
        for(int i = 0; i < ID_POOLS; ++i)
        {
            fPoolIdLocks[i] = new Object();
            fPoolIds[i] = i * ID_POOL_SIZE;
        }
        fIdPoolCounter = 0;

        fTrigger = new Object();

        fPendingQueries = new HashMap<Integer, WebsockQuery>();
        fPendingMessages = new HashMap<Integer, IMessageCallback>();
        fPendingResults = new HashMap<Integer, IQueryCallback>();

        fTimeouts = new HashMap<Integer, Long>();
        fRetries = new HashMap<Integer, Integer>();
        fMultiCounters = new HashMap<Integer, Integer>();

        fProcedureQueries = new HashMap<String, WebsockQuery>();

        fLogger = Logger.getLogger(this.getClass().getName());
        fDebug = (fLogger.getLevel() == Level.FINEST);

        fTimeout = DEFAULT_TIMEOUT_MS;
        fTimerInt = DEFAULT_TIMER_MS;
        fRetryNum = DEFAULT_RETRIES;

        fResendProcedures = true;
    }

    /**
     * @param utils transfer utilities to use
     */
    public void setTransferUtils(List<TransferUtil> utils)
    {
        fSessionPool.clear();
        fSessionPool.addAll(utils);

        if(fResendProcedures)
        {
            for(TransferUtil util : utils)
            {
                //re-create runtime stored procedures for new server
                resendProcedureQueries(util);
            }
        }
    }

    /**
     * @param util transfer utility to add to the pool
     */
    public void addTransferUtil(TransferUtil util)
    {
        if(util != null)
        {
            fSessionPool.add(util);

            if(fResendProcedures)
            {
                //re-create runtime stored procedures for new server
                resendProcedureQueries(util);
            }
        }
    }

    /**
     * @param util transfer utility to remove from the pool
     */
    public void removeTransferUtil(TransferUtil util)
    {
        if(util != null)
        {
            fSessionPool.remove(util);
        }
    }

    /**
     * Clears all transfer utilities from the handler. Use with caution, in an
     * active deployment this may cause the handler to malfunction.
     */
    public void clearTransferUtils()
    {
        fSessionPool.clear();
    }

    @Override
    public long getTimeout()
    {
        return fTimeout;
    }

    @Override
    public void setTimeout(long timeout)
    {
        //TODO: synchronization?

        fTimeout = timeout;

        synchronized(fTrigger)
        {
            fTrigger.notify();
        }
    }

    /**
     * @return number of milliseconds between timeout checks
     */
    public long getTimerInterval()
    {
        return fTimerInt;
    }

    /**
     * Sets the number of milliseconds to wait after each check for timeouts of
     * pending queries.
     * 0 and negative values are ignored.
     *
     * @param timerInterval milliseconds between timeout checks
     */
    public void setTimerInterval(long timerInterval)
    {
        if(timerInterval > 0)
        {
            fTimerInt = timerInterval;

            synchronized(fTrigger)
            {
                fTrigger.notify();
            }
        }
    }

    /**
     * @return number of retries before a query is cancelled.
     */
    public int getRetryCount()
    {
        return fRetryNum;
    }

    /**
     * Sets the number of attempts to re-send a query after the first attempt
     * timed out. 0 or a negative number will deactivate retries.
     *
     * @param retries number of retries
     */
    public void setRetryCount(int retries)
    {
        fRetryNum = retries;
    }

    /**
     * @return whether runtime stored procedures will be recreated for new
     *      connections
     */
    public boolean isResendProcedures()
    {
        return fResendProcedures;
    }

    /**
     * @param resendProcedures whether runtime stored procedures will be
     *      recreated for new connections
     */
    public void setResendProcedures(boolean resendProcedures)
    {
        fResendProcedures = resendProcedures;
    }

    @Override
    public boolean handleMessage(final WebsockQuery message)
    {
        boolean handled = false;
        int id = message.getId();
        EQueryType type = message.getType();

        IMessageCallback mcb = fPendingMessages.get(id);
        IQueryCallback qcb = fPendingResults.get(id);

        //messages sent to multiple servers
        synchronized(fMultiCounters)
        {
            Integer count = fMultiCounters.get(id);

            if(count != null)
            {
                if(--count > 0)
                {
                    //wait for all responses
                    fMultiCounters.put(id, count);

                    mcb = null;
                    qcb = null;
                    handled = true;
                }
                else
                {
                    fMultiCounters.remove(id);
                }
            }
        }

        //query results
        if(qcb != null)
        {
            if(type != EQueryType.ERROR)
            {
                @SuppressWarnings("unchecked")
                Map<String, Object> map =
                    (Map<String, Object>) message.getPayload();
                AResultSet<?> set = ResultSetConverter.toResultSet(map);
                qcb.setResult(set);
            }
            else if(message.getPayload() != null)
            {
                qcb.setErrorMessage(message.getPayload().toString());
            }
            else
            {
                qcb.setErrorMessage(null);
            }

            handled = true;
            done(id);
        }
        //message responses
        else if(mcb != null)
        {
            if(type != EQueryType.ERROR)
            {
                mcb.setResponse(message);
            }
            else if(message.getPayload() != null)
            {
                mcb.setErrorMessage(message.getPayload().toString());
            }
            else
            {
                mcb.setErrorMessage(null);
            }

            handled = true;
            done(id);
        }


        if(handled)
        {
            if(fDebug)
            {
                fLogger.log(Level.FINEST, "query " + id + ":" + type
                    + " handled; clearing");
            }
        }

        /*
         * otherwise it may be an unrelated query, not directly handled by the
         * query handler
         */
        return handled;
    }

    @Override
    public IMessageCallback sendMessage(WebsockQuery message)
    {
        MessageFuture future = new MessageFuture();

        sendMessage(message, future);

        return future;
    }

    @Override
    public IQueryCallback sendQuery(WebsockQuery query)
    {
        ResultFuture future = new ResultFuture();

        sendQuery(query, future);

        return future;
    }

    @Override
    public void sendMessage(final WebsockQuery message,
        IMessageCallback callback)
    {
        //check if any session is open
        if(fSessionPool.isEmpty())
        {
            //fail if there is no connection available
            callback.setErrorMessage("no connection available");
            return;
        }

        final int id = getId();
        message.setId(id);

        synchronized(fPendingQueries)
        {
            fPendingQueries.put(id, message);
        }
        synchronized(fPendingMessages)
        {
            fPendingMessages.put(id, callback);
        }
        synchronized(fTimeouts)
        {
            fTimeouts.put(id, System.currentTimeMillis());
        }

        try
        {
            switch(message.getType())
            {
                case PROCEDURE_CALL:
                case DIRECT_CYPHER:
                    sendToAny(message);
                    break;

                default:
                    sendToAll(message);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fLogger.log(Level.SEVERE, "failed to send message to server", e);
            callback.setErrorMessage("failed to send message to server");
            done(id);
        }
    }

    @Override
    public void sendQuery(final WebsockQuery query, IQueryCallback callback)
    {
        //check if any session is open
        if(fSessionPool.isEmpty())
        {
            //fail if there is no connection available
            callback.setErrorMessage("no connection available");
            return;
        }

        final int id = getId();
        query.setId(id);

        synchronized(fPendingQueries)
        {
            fPendingQueries.put(id, query);
        }
        synchronized(fPendingResults)
        {
            fPendingResults.put(id, callback);
        }
        synchronized(fTimeouts)
        {
            fTimeouts.put(id, System.currentTimeMillis());
        }

        try
        {
            switch(query.getType())
            {
                case PROCEDURE_CALL:
                case DIRECT_CYPHER:
                    sendToAny(query);
                    break;

                default:
                    sendToAll(query);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fLogger.log(Level.SEVERE, "failed to send query to server", e);
            callback.setErrorMessage("failed to send message to server");
            done(id);
        }
    }

    @Override
    public IMessageCallback sendDirectMessage(WebsockQuery message,
        TransferUtil util)
    {
        MessageFuture future = new MessageFuture();

        sendDirectMessage(message, future, util);

        return future;
    }

    @Override
    public void sendDirectMessage(WebsockQuery message,
        IMessageCallback callback, TransferUtil util)
    {
        if(util == null)
        {
            callback.setErrorMessage("no connection available");
            return;
        }

        final int id = getId();
        message.setId(id);

        synchronized(fPendingQueries)
        {
            fPendingQueries.put(id, message);
        }
        synchronized(fPendingMessages)
        {
            fPendingMessages.put(id, callback);
        }
        synchronized(fTimeouts)
        {
            fTimeouts.put(id, System.currentTimeMillis());
        }

        try
        {
            util.sendMessage(message);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fLogger.log(Level.SEVERE, "failed to send message to server", e);
            callback.setErrorMessage("failed to send message to server");
            done(id);
        }
    }

    private void sendToAny(final WebsockQuery message) throws Exception
    {
        //TODO: synchronization?
        final int poolInd = fPoolCounter++ % fSessionPool.size();
        final TransferUtil remote = fSessionPool.get(poolInd);
        remote.sendMessage(message);
    }

    private void sendToAll(final WebsockQuery message) throws Exception
    {
        //TODO: synchronization?

        synchronized(fMultiCounters)
        {
            fMultiCounters.put(message.getId(), fSessionPool.size());
        }

        for(TransferUtil remote : fSessionPool)
        {
            remote.sendMessage(message);
        }
    }

    private void resendProcedureQueries(final TransferUtil util)
    {
        //TODO: synchronization?

        int id = 0;
        WebsockQuery query = null;
        WebsockQuery oldQuery = null;
        IMessageCallback callback = null;

        for(Entry<String, WebsockQuery> procQueryE
            : fProcedureQueries.entrySet())
        {
            //create query with equivalent data
            oldQuery = procQueryE.getValue();
            query = new WebsockQuery(oldQuery.getType());

            id = getId();
            query.setId(id);

            query.setParameters(oldQuery.getParameters());
            query.setPayload(oldQuery.getPayload());

            //send query
            try
            {
                callback = new MessageFuture();
                fPendingMessages.put(id, callback);

                util.sendMessage(query);
            }
            catch(Exception e)
            {
                fLogger.log(Level.SEVERE,
                    "failed to replay procedure creation: "
                    + procQueryE.getKey(), e);
            }
        }
    }

    @Override
    public int getId()
    {
        final int idIndex = ++fIdPoolCounter % ID_POOLS;
        int id = 1;

        synchronized(fPoolIdLocks[idIndex])
        {
            id = fPoolIds[idIndex]++;

            //wrap on overflow
            if(fPoolIds[idIndex] == ID_POOL_SIZE * (idIndex + 1))
            {
                fPoolIds[idIndex]= ID_POOL_SIZE * idIndex;
            }
        }

        return id;
    }

    private void done(int id)
    {
        synchronized(fPendingMessages)
        {
            fPendingMessages.remove(id);
        }
        synchronized(fPendingQueries)
        {
            fPendingQueries.remove(id);
        }
        synchronized(fPendingResults)
        {
            fPendingResults.remove(id);
        }
        synchronized(fTimeouts)
        {
            fTimeouts.remove(id);
        }
        synchronized(fRetries)
        {
            fRetries.remove(id);
        }
    }

    @Override
    public void cancel(int id)
    {
        synchronized(fPendingMessages)
        {
            IErrorFuture<?> fut = fPendingMessages.get(id);
            if(fut != null)
            {
                fut.cancel(true);
            }
        }

        synchronized(fPendingResults)
        {
            IErrorFuture<?> fut = fPendingResults.get(id);
            if(fut != null)
            {
                fut.cancel(true);
            }
        }

        done(id);
    }

    @Override
    public void run()
    {
        fActive = true;

        long maxTime = 0;
        final Set<Integer> timedOut = new HashSet<Integer>();

        while(fActive)
        {
            //check for timeouts if activated
            if(fTimeout > 0)
            {
                maxTime = System.currentTimeMillis() - fTimeout;

                synchronized(fTimeouts)
                {
                    for(Entry<Integer, Long> timeE : fTimeouts.entrySet())
                    {
                        if(timeE.getValue() < maxTime)
                        {
                            timedOut.add(timeE.getKey());
                        }
                    }
                }

                for(Integer id : timedOut)
                {
                    timeout(id);
                }
                timedOut.clear();
            }

            try
            {
                synchronized(fTrigger)
                {
                    fTrigger.wait(fTimerInt);
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private void timeout(final int id)
    {
        //TODO: synchronization?

        final WebsockQuery query = fPendingQueries.get(id);

        if(query == null)
        {
            fLogger.log(Level.SEVERE, "missing query " + id + " timed out");
            done(id);
            return;
        }

        fLogger.log(Level.WARNING, "query timed out:\n" + query.getPayload());

        //retry if retries left, cancel otherwise
        Integer retries = fRetries.get(id);
        if(retries == null)
        {
            retries = 0;
        }

        if(retries < fRetryNum)
        {
            ++retries;
            fRetries.put(id, retries);
            fLogger.log(Level.WARNING, "retry " + retries + " for query "
                + query.getId());

            synchronized(fTimeouts)
            {
                fTimeouts.put(id, System.currentTimeMillis());
            }

            try
            {
                if(fSessionPool.isEmpty())
                {
                    //cancel query if no connections are available
                    IErrorFuture<?> fut = fPendingMessages.get(id);
                    if(fut != null)
                    {
                        fut.setErrorMessage("no connections availabe");
                    }

                    fut = fPendingResults.get(id);
                    if(fut != null)
                    {
                        fut.setErrorMessage("no connections availabe");
                    }

                    done(id);
                }
                else
                {
                    switch(query.getType())
                    {
                        case PROCEDURE_CALL:
                        case DIRECT_CYPHER:
                            sendToAny(query);
                            break;

                        default:
                            sendToAll(query);
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
                fLogger.log(Level.SEVERE,
                    "failed to retry sending query to server", e);
            }
        }
        else
        {
            //otherwise cancel - error
            IErrorFuture<?> fut = fPendingMessages.get(id);
            if(fut != null)
            {
                fut.setErrorMessage("timeout error");
            }

            fut = fPendingResults.get(id);
            if(fut != null)
            {
                fut.setErrorMessage("timeout error");
            }

            fLogger.log(Level.SEVERE, "query "+ query.getId() + " cancelled "
                + "(no retries left)");

            done(id);
        }

    }

    @Override
    public void deactivate()
    {
        fActive = false;

        synchronized(fTrigger)
        {
            fTrigger.notify();
        }
    }
}
