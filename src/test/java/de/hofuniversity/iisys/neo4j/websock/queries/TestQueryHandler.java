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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Assert;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.TransferUtil;
import de.hofuniversity.iisys.neo4j.websock.result.AResultSet;

/**
 * Mock implementation for a query handler, comparing incoming queries to a
 * predefined instance and if they are identical, returns a predefined response
 * or result set. Otherwise error messages are generated and returned via
 * callbacks.
 * One instance is only really useful for one specific call.
 */
public class TestQueryHandler implements IQueryHandler
{
    private final WebsockQuery fQuery, fResponse;
    private final AResultSet<?> fResult;

    /**
     * Creates a mock query handler returning the given response to the given
     * query.
     *
     * @param query query to match incoming queries with
     * @param response
     */
    public TestQueryHandler(WebsockQuery query, WebsockQuery response)
    {
        fQuery = query;
        fResponse = response;
        fResult = null;
    }

    /**
     * Creates a mock query handler returning the given result to the given
     * query.
     *
     * @param query query to match incoming queries with
     * @param response result to return for the right query
     */
    public TestQueryHandler(WebsockQuery query, AResultSet<?> response)
    {
        fQuery = query;
        fResult = response;
        fResponse = null;
    }

    private boolean matching(final WebsockQuery query)
    {
        boolean matches = true;

        Assert.assertEquals(fQuery.getId(), query.getId());
        Assert.assertEquals(fQuery.getType(), query.getType());

        //parameters
        final Map<String, Object> targetParams = fQuery.getParameters();
        final Map<String, Object> params = query.getParameters();

        for(Entry<String, Object> paramE : targetParams.entrySet())
        {
            deepEqual(paramE.getValue(), params.get(paramE.getKey()));
        }

        //payload
        Object targetPayload = fQuery.getPayload();
        Object payload = fQuery.getPayload();

        if(!(targetPayload == null && payload == null))
        {
            deepEqual(targetPayload, payload);
        }

        return matches;
    }

    private void deepEqual(final Object exp, final Object act)
    {
        if(exp instanceof List<?>)
        {
            //TODO: deep equals?
            Assert.assertTrue(act instanceof List<?>);
            final List<?> target = (List<?>) exp;
            final List<?> actual = (List<?>) act;

            for(Object o : target)
            {
                actual.contains(o);
            }
        }
        else if(exp instanceof Map<?, ?>)
        {
            Assert.assertTrue(act instanceof Map<?, ?>);
            final Map<?, ?> target = (Map<?, ?>) exp;
            final Map<?, ?> actual = (Map<?, ?>) act;

            for(Entry<?, ?> mapE : target.entrySet())
            {
                deepEqual(mapE.getValue(), actual.get(mapE.getKey()));
            }
        }
        else if(exp instanceof Object[])
        {
            Assert.assertTrue(act instanceof Object[]);

            final Object[] target = (Object[]) exp;
            final Object[] actual = (Object[]) act;
            Assert.assertEquals(target.length, actual.length);

            for(int i = 0; i < target.length; ++i)
            {
                deepEqual(target[i], actual[i]);
            }
        }
        //TODO: proper way to make exceptions for generated values
        else if(exp != null)
        {
            Assert.assertEquals(exp, act);
        }
    }

    @Override
    public void run()
    {
        //not needed
    }

    @Override
    public boolean handleMessage(WebsockQuery message)
    {
        //not needed
        return false;
    }

    @Override
    public IMessageCallback sendMessage(WebsockQuery message)
    {
        MessageFuture future = new MessageFuture();

        // TODO
        if(matching(message))
        {
            future.setResponse(fResponse);
        }
        else
        {
            future.setErrorMessage("query mismatch");
        }

        return null;
    }

    @Override
    public IQueryCallback sendQuery(WebsockQuery query)
    {
        ResultFuture future = new ResultFuture();

        //TODO
        if(matching(query))
        {
            future.setResult(fResult);
        }
        else
        {
            future.setErrorMessage("query mismatch");
        }

        return future;
    }

    @Override
    public void sendMessage(WebsockQuery message, IMessageCallback callback)
    {
        //TODO
        if(matching(message))
        {
            callback.setResponse(fResponse);
        }
        else
        {
            callback.setErrorMessage("query mismatch");
        }
    }

    @Override
    public void sendQuery(WebsockQuery query, IQueryCallback callback)
    {
        //TODO
        if(matching(query))
        {
            callback.setResult(fResult);
        }
        else
        {
            callback.setErrorMessage("query mismatch");
        }
    }

    @Override
    public int getId()
    {
        //not needed
        return 0;
    }

    @Override
    public void cancel(int id)
    {
        //not needed
    }

    @Override
    public void deactivate()
    {
        //not needed
    }

    @Override
    public void addTransferUtil(TransferUtil util)
    {
        //not needed
    }

    @Override
    public void removeTransferUtil(TransferUtil util)
    {
        //not needed
    }

    @Override
    public long getTimeout()
    {
        //not needed
        return 0;
    }

    @Override
    public void setTimeout(long timeout)
    {
        //not needed
    }

    @Override
    public IMessageCallback sendDirectMessage(WebsockQuery message,
        TransferUtil util)
    {
        //not needed
        return null;
    }

    @Override
    public void sendDirectMessage(WebsockQuery message,
        IMessageCallback callback, TransferUtil util)
    {
        //not needed
    }

}
