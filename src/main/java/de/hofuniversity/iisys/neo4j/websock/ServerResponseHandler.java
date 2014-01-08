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

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.Session;
import javax.websocket.RemoteEndpoint.Basic;

import de.hofuniversity.iisys.neo4j.websock.queries.IQueryHandler;
import de.hofuniversity.iisys.neo4j.websock.query.EQueryType;
import de.hofuniversity.iisys.neo4j.websock.query.IMessageHandler;
import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.BinaryTransferUtil;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.StringTransferUtil;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.TransferUtil;
import de.hofuniversity.iisys.neo4j.websock.session.WebsockSession;

/**
 * Websocket handler for incoming server responses.
 */
public class ServerResponseHandler implements IMessageHandler
{
    private final WebsockSession fWsSess;
    private final Session fSession;
    private final IQueryHandler fQueryHandler;

    private final TransferUtil fTransfer;

    private final Logger fLogger;
    private final boolean fDebug;

    /**
     * Creates a server response handler for the given websocket session, using
     * the given query handler, sending in the given format.
     * Parameters must not be null.
     *
     * @param wsSess websocket session to use
     * @param qHandler query handler to use
     * @param format format to send in
     * @param comp whether to use compression
     */
    public ServerResponseHandler(WebsockSession wsSess, IQueryHandler qHandler,
        String format, String comp)
    {
        if(wsSess == null)
        {
            throw new NullPointerException("websocket session was null");
        }
        if(qHandler == null)
        {
            throw new NullPointerException("query handler was null");
        }
        if(format == null)
        {
            throw new NullPointerException("format parameter was null");
        }
        if(comp == null)
        {
            throw new NullPointerException("compression parameter was null");
        }

        fWsSess = wsSess;
        fSession = fWsSess.getSession();
        fQueryHandler = qHandler;

        fLogger = Logger.getLogger(this.getClass().getName());
        fDebug = (fLogger.getLevel() == Level.FINEST);

        Basic remote = fSession.getBasicRemote();
        StringTransferUtil stUtil = new StringTransferUtil(remote, this);
        BinaryTransferUtil btUtil = new BinaryTransferUtil(remote, this,
            true);
        fTransfer = new TransferUtil(stUtil, btUtil);

        fTransfer.setFormat(format, comp);

        fSession.addMessageHandler(stUtil);
        fSession.addMessageHandler(btUtil);
    }

    @Override
    public void dispose()
    {
        fQueryHandler.deactivate();
    }

    /**
     * @return transfer utility for this response handler
     */
    public TransferUtil getTransferUtil()
    {
        return fTransfer;
    }

    private void handle(final WebsockQuery msg)
    {
        //try query manager
        if(fQueryHandler.handleMessage(msg))
        {
            return;
        }

        if(fDebug)
        {
            fLogger.log(Level.FINEST, "query " + msg.getId() + ":"
                + msg.getType() + " received");
        }

        switch(msg.getType())
        {
            case RESULT:
                handleResult(msg);
                break;

            case SUCCESS:
                handleSuccess(msg);
                break;

            case PING:
                handlePing(msg);
                break;

            case PONG:
                handlePong(msg);
                break;

            case ERROR:
                handleError(msg);
                break;
        }
    }

    @Override
    public void onMessage(ByteBuffer buffer)
    {
        try
        {
            WebsockQuery query = fTransfer.convert(buffer);
            handle(query);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            fLogger.log(Level.SEVERE, "failed to handle binary message", e);
        }
    }

    @Override
    public void onMessage(String message)
    {
        try
        {
            WebsockQuery query = fTransfer.convert(message);
            handle(query);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            fLogger.log(Level.SEVERE, "failed to handle text message", e);
        }
    }

    private void handleResult(final WebsockQuery msg)
    {
        fLogger.log(Level.WARNING, "unhandled query result: " + msg.getId());
    }

    private void handleSuccess(final WebsockQuery msg)
    {
        fLogger.log(Level.WARNING, "unhandled query success: " + msg.getId());
    }

    private void handlePing(final WebsockQuery msg)
    {
        WebsockQuery pong = new WebsockQuery(msg.getId(), EQueryType.PONG);
        try
        {
            fTransfer.sendMessage(pong);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void handlePong(final WebsockQuery msg)
    {
        //TODO: measure time taken or refresh watchdog
    }

    private void handleError(final WebsockQuery msg)
    {
        fLogger.log(Level.SEVERE, "unhandled error: " + msg.getId() + ": "
            + msg.getPayload());
    }
}
