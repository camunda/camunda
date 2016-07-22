package org.camunda.tngp.client.impl;

import java.util.concurrent.ExecutionException;

import org.camunda.tngp.client.impl.cmd.AbstractCmdImpl;
import org.camunda.tngp.client.impl.cmd.ClientRequestWriter;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class ClientCmdExecutor
{
    protected final TransportConnectionPool connectionPool;
    protected final ClientChannelResolver clientChannelResolver;

    public ClientCmdExecutor(final TransportConnectionPool connectionPool, final ClientChannelResolver clientChannelResolver)
    {
        this.connectionPool = connectionPool;
        this.clientChannelResolver = clientChannelResolver;
    }

    public <R> R execute(final AbstractCmdImpl<R> cmd)
    {
        try (final TransportConnection connection = connectionPool.openConnection())
        {
            if (connection != null)
            {
                return execute(cmd, connection);
            }
            else
            {
                throw new RuntimeException("Request failed: no connection available.");
            }
        }
    }

    public <R> R execute(final AbstractCmdImpl<R> cmd, final TransportConnection connection)
    {
        try
        {
            return executeAsync(cmd, connection).get();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted while executing command");
        }
        catch (ExecutionException e)
        {
            throw (RuntimeException) e.getCause();
        }
    }

    public <R> ResponseFuture<R> executeAsync(final AbstractCmdImpl<R> cmd, final TransportConnection connection)
    {
        final ClientRequestWriter requestWriter = cmd.getRequestWriter();
        requestWriter.validate();

        final int channelId = clientChannelResolver.getChannelIdForCmd(cmd);

        final PooledTransportRequest request = connection.openRequest(channelId, requestWriter.getLength());

        if (request != null)
        {
            try
            {
                writeRequest(request, requestWriter);
                request.commit();
                return new ResponseFuture<>(request, cmd.getResponseHandler());
            }
            catch (Exception e)
            {
                request.abort();
                throw new RuntimeException("Failed to write request", e);
            }
        }
        else
        {
            throw new RuntimeException("Failed to open request");
        }
    }

    private static void writeRequest(final PooledTransportRequest request, final BufferWriter requestWriter)
    {
        final int offset = request.getClaimedOffset();
        final MutableDirectBuffer buffer = request.getClaimedRequestBuffer();

        requestWriter.write(buffer, offset);
    }
}
