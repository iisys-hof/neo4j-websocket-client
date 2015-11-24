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

import java.nio.ByteBuffer;

import de.hofuniversity.iisys.neo4j.websock.query.IMessageHandler;

/**
 * Empty IMessageHandler implementation.
 */
public class NopMessageHandler implements IMessageHandler
{
    @Override
    public void onMessage(ByteBuffer message) {}

    @Override
    public void onMessage(String message) {}

    @Override
    public void dispose() {}
}
