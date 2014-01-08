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
