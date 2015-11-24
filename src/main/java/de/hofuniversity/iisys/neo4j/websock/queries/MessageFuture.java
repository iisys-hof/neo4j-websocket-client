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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;

/**
 * Basic implementation for a future returning the response to a sent message
 * or an error message.
 */
public class MessageFuture implements IMessageCallback
{
    private final Object fTrigger = new Object();

    private WebsockQuery fResponse;
    private boolean fCancelled = false, fDone = false;
    private String fErrorMessage;

    @Override
    public boolean cancel(boolean arg0)
    {
        fCancelled = true;
        fDone = true;

        synchronized(fTrigger)
        {
            fTrigger.notify();
        }

        return fCancelled;
    }

    @Override
    public WebsockQuery get() throws InterruptedException, ExecutionException
    {
        WebsockQuery response = null;

        try
        {
            response = get(0, TimeUnit.DAYS);
        }
        catch(TimeoutException e)
        {
            throw new ExecutionException("timeout", e);
        }

        return response;
    }

    @Override
    public WebsockQuery get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException
    {
        if(unit != null)
        {
            timeout = TimeUnit.MILLISECONDS.convert(timeout, unit);
        }

        if(!fDone)
        {
            synchronized(fTrigger)
            {
                if(timeout <= 0)
                {
                    fTrigger.wait();
                }
                else
                {
                    fTrigger.wait(timeout);
                }
            }
        }

        if(fErrorMessage != null)
        {
            throw new ExecutionException(fErrorMessage, null);
        }
        else if(fCancelled)
        {
            throw new InterruptedException("cancelled");
        }

        return fResponse;
    }

    @Override
    public boolean isCancelled()
    {
        return fCancelled;
    }

    @Override
    public boolean isDone()
    {
        return fDone;
    }

    @Override
    public void setErrorMessage(String message)
    {
        fErrorMessage = message;
        fDone = true;

        synchronized(fTrigger)
        {
            fTrigger.notify();
        }
    }

    @Override
    public String getErrorMessage()
    {
        return fErrorMessage;
    }

    @Override
    public void setResponse(WebsockQuery message)
    {
        fResponse = message;
        fDone = true;

        synchronized(fTrigger)
        {
            fTrigger.notify();
        }
    }

}
