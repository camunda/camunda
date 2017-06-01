package org.camunda.tngp.client.impl;

import java.util.concurrent.ExecutionException;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.client.impl.cmd.AbstractCmdImpl;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.camunda.tngp.util.buffer.RequestWriter;


public class ClientCmdExecutor
{
    private final TransportManager transportManager;

    public ClientCmdExecutor(final TransportManager transportManager)
    {

        this.transportManager = transportManager;
    }

    public <R> R execute(final AbstractCmdImpl<R> cmd)
    {
        try (TransportConnection connection = transportManager.openConnection())
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
        final RequestWriter requestWriter = cmd.getRequestWriter();
        requestWriter.validate();

        final Channel channel = transportManager.getChannelForCommand(cmd);

        final PooledTransportRequest request = connection.openRequest(channel.getStreamId(), requestWriter.getLength());

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
