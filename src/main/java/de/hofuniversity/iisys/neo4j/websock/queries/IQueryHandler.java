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

import de.hofuniversity.iisys.neo4j.websock.query.WebsockQuery;
import de.hofuniversity.iisys.neo4j.websock.query.encoding.TransferUtil;

/**
 * Handler for incoming and outgoing messages that handles responses to
 * previously sent messages and queries.
 */
public interface IQueryHandler extends Runnable
{
    /**
     * Handles incoming messages that are responses to or errors for previously
     * sent messages and queries.
     *
     * @param message incoming message
     * @return whether the message could be handled
     */
    public boolean handleMessage(WebsockQuery message);

    /**
     * Sends a message, using the default callback implementation and a new ID.
     * The message given message must not be null.
     *
     * @param message message to send
     * @return default callback for this message
     */
    public IMessageCallback sendMessage(WebsockQuery message);

    /**
     * Sends a query, using the default callback implementation and a new ID.
     * The query given query must not be null.
     *
     * @param query query to send
     * @return default callback for this query
     */
    public IQueryCallback sendQuery(WebsockQuery query);

    /**
     * Sends a message, using the given callback and a new ID.
     * The message and callback given must not be null.
     *
     * @param message message to send
     * @param callback callback to notify
     */
    public void sendMessage(WebsockQuery message, IMessageCallback callback);

    /**
     * Sends a query, using the given callback and a new ID.
     * The message and callback given must not be null.
     *
     * @param query query to send
     * @param callback callback to notify
     */
    public void sendQuery(WebsockQuery query, IQueryCallback callback);

    /**
     * Sends a message, using the default callback implementation and a new ID
     * directly using the given transfer utility.
     * The message given parameters must not be null.
     *
     * @param message message to send
     * @param util transfer utility to send with
     * @return default callback
     */
    public IMessageCallback sendDirectMessage(WebsockQuery message,
        TransferUtil util);

    /**
     * Sends a message, using the given callback and a new ID directly using
     * the given transfer utility.
     * The parameters given must not be null.
     *
     * @param message message to send
     * @param callback callback to notify
     * @param util transfer utility to send with
     */
    public void sendDirectMessage(WebsockQuery message, IMessageCallback callback,
        TransferUtil util);

    /**
     * @return new ID, unique until the first integer overflow
     */
    public int getId();

    /**
     * Cancels and clears the message or query with the given ID.
     *
     * @param id ID of the request to cancel
     */
    public void cancel(int id);

    /**
     * Stops the internal timeout and retry mechanism.
     */
    public void deactivate();

    /**
     * @param util transfer utility to add to the handler
     */
    public void addTransferUtil(TransferUtil util);

    /**
     * @param util transfer utility to remove from the handler
     */
    public void removeTransferUtil(TransferUtil util);

    /**
     * @return number of milliseconds for query response timeouts
     */
    public long getTimeout();

    /**
     * Sets the number of milliseconds before retrying to send a query or
     * canceling it and triggers a check.
     * Values of 0 and less deactivate the timeout mechanism.
     *
     * @param timeout in milliseconds
     */
    public void setTimeout(long timeout);
}
