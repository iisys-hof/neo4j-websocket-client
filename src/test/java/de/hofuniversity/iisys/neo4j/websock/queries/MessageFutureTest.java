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

import org.junit.Assert;
import org.junit.Test;

import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;

/**
 * Test for the default message response future implementation.
 */
public class MessageFutureTest
{
    private static final String ERROR_MESSAGE = "error occurred";

    /**
     * Tests the successful retrieval of a result set.
     */
    @Test
    public void retrievalTest() throws Exception
    {
        //sequence
        MessageFuture future1 = new MessageFuture();

        final WebsockQuery response = new WebsockQuery(42, EQueryType.PONG);
        future1.setResponse(response);

        Assert.assertEquals(response, future1.get());

        //asynchronous
        final MessageFuture future2 = new MessageFuture();

        new Thread(new Runnable(){

            @Override
            public void run()
            {
                try
                {
                    Thread.sleep(500);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                future2.setResponse(response);
            }

        }).start();

        Assert.assertEquals(response, future2.get());
    }

    /**
     * Tests the future's error handling.
     */
    @Test
    public void errorTest() throws Exception
    {
        //sequence
        MessageFuture future1 = new MessageFuture();

        future1.setErrorMessage(ERROR_MESSAGE);

        boolean error = false;

        try
        {
            future1.get();
        }
        catch(Exception e)
        {
            error = true;
        }

        Assert.assertTrue(error);
        Assert.assertEquals(ERROR_MESSAGE, future1.getErrorMessage());

        //asynchronous
        final MessageFuture future2 = new MessageFuture();

        new Thread(new Runnable(){

            @Override
            public void run()
            {
                try
                {
                    Thread.sleep(500);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                future2.setErrorMessage(ERROR_MESSAGE);
            }

        }).start();

        error = false;
        try
        {
            future2.get();
        }
        catch(Exception e)
        {
            error = true;
        }
        Assert.assertTrue(error);
        Assert.assertEquals(ERROR_MESSAGE, future2.getErrorMessage());
    }

    /**
     * Tests the cancellation of a future.
     */
    @Test
    public void cancelTest()
    {
        //sequence
        MessageFuture future1 = new MessageFuture();

        future1.cancel(true);

        boolean error = false;

        try
        {
            future1.get();
        }
        catch(Exception e)
        {
            error = true;
        }

        Assert.assertTrue(error);

        //asynchronous
        final MessageFuture future2 = new MessageFuture();

        new Thread(new Runnable(){

            @Override
            public void run()
            {
                try
                {
                    Thread.sleep(500);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                future2.cancel(true);
            }

        }).start();

        error = false;
        try
        {
            future2.get();
        }
        catch(Exception e)
        {
            error = true;
        }
        Assert.assertTrue(error);
    }
}
