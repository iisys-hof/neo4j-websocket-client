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
package de.hofuniversity.iisys.neo4j.websock.queries;

import java.util.LinkedList;

import javax.websocket.RemoteEndpoint.Basic;

import org.junit.Assert;

import org.junit.Test;

import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.BinaryTransferUtil;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.StringTransferUtil;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.TransferUtil;
import de.hofuniversity.iisys.neo4j.websock.result.AResultSet;

/**
 * Test for the query handler, handling responses for queries and messages as
 * well as enforcing timeouts.
 */
public class BasicQueryHandlerTest
{
    private static final long TIMEOUT_MS = 200;
    private static final long TIMER_MS = 25;
    private static final int RETRIES = 2;

    /**
     * Tests the normal query-response system of the query handler.
     */
    @Test
    public void responseTest() throws Exception
    {
        BasicQueryHandler handler = new BasicQueryHandler();

        FakeWebsockSession session = new FakeWebsockSession();
        Basic remote = session.getBasicRemote();
        LinkedList<WebsockQuery> responses = session.getResponses();

        StringTransferUtil stUtil = new StringTransferUtil(remote,
            new NopMessageHandler());

        BinaryTransferUtil btUtil = new BinaryTransferUtil(remote,
            new NopMessageHandler(), true);

        TransferUtil util = new TransferUtil(stUtil, btUtil);
        handler.setTransferUtil(util);

        handler.setTimeout(TIMEOUT_MS);
        handler.setTimerInterval(TIMER_MS);
        handler.setRetryCount(RETRIES);

        //send message via handler
        IMessageCallback mFuture = handler.sendMessage(new WebsockQuery(
            EQueryType.PING));

        //feed in response manually
        WebsockQuery response = responses.pop();
        int id = response.getId();
        handler.handleMessage(response);

        //check response
        response = mFuture.get();
        Assert.assertEquals(EQueryType.PONG, response.getType());

        //send empty query via handler
        IQueryCallback rFuture = handler.sendQuery(new WebsockQuery(
            EQueryType.DIRECT_CYPHER));

        //feed in response manually
        response = responses.pop();
        Assert.assertNotEquals(id, response.getId());
        handler.handleMessage(response);

        //check response
        AResultSet<?> set = rFuture.get();
        Assert.assertNotNull(set);
    }

    /**
     * Tests the timeout mechanism of the query handler.
     */
    @Test
    public void timeoutTest() throws Exception
    {
        BasicQueryHandler handler = new BasicQueryHandler();

        FakeWebsockSession session = new FakeWebsockSession();
        Basic remote = session.getBasicRemote();
        LinkedList<WebsockQuery> responses = session.getResponses();

        StringTransferUtil stUtil = new StringTransferUtil(remote,
            new NopMessageHandler());

        BinaryTransferUtil btUtil = new BinaryTransferUtil(remote,
            new NopMessageHandler(), true);

        TransferUtil util = new TransferUtil(stUtil, btUtil);
        handler.setTransferUtil(util);

        handler.setTimeout(TIMEOUT_MS);
        handler.setTimerInterval(TIMER_MS);
        handler.setRetryCount(RETRIES);
        new Thread(handler).start();

        //send message via handler, but do not feed responses back in
        IMessageCallback mFuture = handler.sendMessage(new WebsockQuery(
            EQueryType.DIRECT_CYPHER));

        //wait for all retries
        int cycles = 0;
        while(cycles++ < RETRIES)
        {
            Assert.assertEquals(cycles, responses.size());
            Thread.sleep(TIMEOUT_MS + TIMER_MS + TIMEOUT_MS / 2);
        }
        Assert.assertEquals(cycles, responses.size());

        //expect exception from timeout
        boolean fail = false;
        try
        {
            mFuture.get();
        }
        catch(Exception e)
        {
            fail = true;
        }
        Assert.assertTrue(fail);

        //send query via handler, but do not feed responses back in
        responses.clear();
        IQueryCallback rFuture = handler.sendQuery(new WebsockQuery(
            EQueryType.DIRECT_CYPHER));

        //wait for all retries, do not expect extra ones
        cycles = 0;
        while(cycles++ < RETRIES)
        {
            Assert.assertEquals(cycles, responses.size());
            Thread.sleep(TIMEOUT_MS + TIMER_MS + TIMEOUT_MS / 2);
        }
        Assert.assertEquals(cycles, responses.size());

        //expect exception from timeout
        fail = false;
        try
        {
            rFuture.get();
        }
        catch(Exception e)
        {
            fail = true;
        }
        Assert.assertTrue(fail);


        handler.deactivate();
    }
}
