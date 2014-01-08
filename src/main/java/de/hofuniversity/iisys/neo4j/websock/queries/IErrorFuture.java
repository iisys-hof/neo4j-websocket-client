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

import java.util.concurrent.Future;

/**
 * Interface for a future that can return with an error message.
 *
 * @param <T> return type of the future
 */
public interface IErrorFuture<T> extends Future<T>
{
    /**
     * Puts the future in an error state and sets the error message.
     *
     * @param message error message to return
     */
    public void setErrorMessage(String message);

    /**
     * @return message for an occurred error or null
     */
    public String getErrorMessage();
}
