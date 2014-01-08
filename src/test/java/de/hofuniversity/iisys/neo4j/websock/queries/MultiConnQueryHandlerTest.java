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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.websocket.RemoteEndpoint.Basic;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.BinaryTransferUtil;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.StringTransferUtil;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.TransferUtil;
import de.hofuniversity.iisys.neo4j.websock.result.AResultSet;

/**
 * Test for the query handler for multiple connections, handling responses for queries and messages
 * as well as enforcing timeouts.
 */
public class MultiConnQueryHandlerTest
{
    private static final long TIMEOUT_MS = 200;
    private static final long TIMER_MS = 25;
    private static final int RETRIES = 2;

    private List<FakeWebsockSession> fSessions;
    private List<LinkedList<WebsockQuery>> fResponses;
    private List<TransferUtil> fTransferUtils;

    /**
     * Sets up a set of fake websocket sessions
     */
    @Before
    public void createSessions()
    {
        fSessions = new ArrayList<FakeWebsockSession>();
        fResponses = new ArrayList<LinkedList<WebsockQuery>>();
        fTransferUtils = new ArrayList<TransferUtil>();

        FakeWebsockSession session = null;
        Basic remote = null;
        StringTransferUtil stUtil = null;
        BinaryTransferUtil btUtil = null;
        TransferUtil transfer = null;

        for(int i = 0; i < 4; ++i)
        {
            session = new FakeWebsockSession();
            remote = session.getBasicRemote();

            stUtil = new StringTransferUtil(remote, new NopMessageHandler());
            btUtil = new BinaryTransferUtil(remote, new NopMessageHandler(), true);
            transfer = new TransferUtil(stUtil, btUtil);

            fSessions.add(session);
            fResponses.add(session.getResponses());
            fTransferUtils.add(transfer);
        }
    }

    /**
     * Tests the normal query-response system of the query handler.
     */
    @Test
    public void responseTest() throws Exception
    {
        final MultiConnQueryHandler handler = new MultiConnQueryHandler();
        handler.setTransferUtils(fTransferUtils);

        //send multi-recipient message via handler
        IMessageCallback mFuture = handler.sendMessage(new WebsockQuery(
            EQueryType.PING));

        //feed in responses manually
        WebsockQuery response = null;
        Set<Integer> ids = new HashSet<Integer>();
        for(LinkedList<WebsockQuery> responses : fResponses)
        {
            response = responses.pop();
            ids.add(response.getId());
            handler.handleMessage(response);
        }

        //check response
        response = mFuture.get();
        Assert.assertEquals(EQueryType.PONG, response.getType());






        //send single-recipient message via handler
        mFuture = handler.sendMessage(new WebsockQuery(
            EQueryType.DIRECT_CYPHER));

        //feed in response manually
        for(LinkedList<WebsockQuery> responses : fResponses)
        {
            if(!responses.isEmpty())
            {
                response = responses.pop();
                ids.add(response.getId());
                handler.handleMessage(response);
                break;
            }
        }

        //check response
        response = mFuture.get();
        Assert.assertEquals(EQueryType.RESULT, response.getType());






        //send empty query via handler
        IQueryCallback rFuture = handler.sendQuery(new WebsockQuery(
            EQueryType.DIRECT_CYPHER));

        //feed in response manually
        for(LinkedList<WebsockQuery> responses : fResponses)
        {
            if(!responses.isEmpty())
            {
                response = responses.pop();
                handler.handleMessage(response);
                break;
            }
        }
        Assert.assertFalse(ids.contains(response.getId()));

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
        //create query handler
        final MultiConnQueryHandler handler = new MultiConnQueryHandler();
        handler.setTransferUtils(fTransferUtils);

        handler.setTimeout(TIMEOUT_MS);
        handler.setTimerInterval(TIMER_MS);
        handler.setRetryCount(RETRIES);
        new Thread(handler).start();

        //send message via handler, but do not feed responses back in
        IMessageCallback mFuture = handler.sendMessage(new WebsockQuery(
            EQueryType.DIRECT_CYPHER));

        //wait for all retries
        int cycles = 0;
        int respCount = 0;
        while(cycles++ < RETRIES)
        {
            respCount = 0;
            for(LinkedList<WebsockQuery> responses : fResponses)
            {
                respCount += responses.size();
            }

            Assert.assertEquals(cycles, respCount);
            Thread.sleep(TIMEOUT_MS + TIMER_MS + TIMEOUT_MS / 2);
        }

        respCount = 0;
        for(LinkedList<WebsockQuery> responses : fResponses)
        {
            respCount += responses.size();
        }
        Assert.assertEquals(cycles, respCount);

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
        for(LinkedList<WebsockQuery> responses : fResponses)
        {
            responses.clear();
        }
        IQueryCallback rFuture = handler.sendQuery(new WebsockQuery(
            EQueryType.DIRECT_CYPHER));

        //wait for all retries, do not expect extra ones
        cycles = 0;
        while(cycles++ < RETRIES)
        {
            respCount = 0;
            for(LinkedList<WebsockQuery> responses : fResponses)
            {
                respCount += responses.size();
            }

            Thread.sleep(TIMEOUT_MS + TIMER_MS + TIMEOUT_MS / 2);
        }

        respCount = 0;
        for(LinkedList<WebsockQuery> responses : fResponses)
        {
            respCount += responses.size();
        }
        Assert.assertEquals(cycles, respCount);

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
