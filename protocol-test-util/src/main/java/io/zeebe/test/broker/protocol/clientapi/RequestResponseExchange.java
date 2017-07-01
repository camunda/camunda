package io.zeebe.test.broker.protocol.clientapi;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.transport.requestresponse.client.PooledTransportRequest;
import io.zeebe.transport.requestresponse.client.TransportConnection;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;

public class RequestResponseExchange
{
    private final TransportConnectionPool connectionPool;
    private final int channelId;

    protected PooledTransportRequest request;
    protected TransportConnection connection;

    public RequestResponseExchange(TransportConnectionPool connectionPool, int channelId)
    {
        this.connectionPool = connectionPool;
        this.channelId = channelId;
    }

    public void sendRequest(BufferWriter writer)
    {
        final int msgLength = writer.getLength();

        connection = connectionPool.openConnection();
        request = connection.openRequest(channelId, msgLength);

        try
        {
            final MutableDirectBuffer buffer = request.getClaimedRequestBuffer();
            final int writeOffset = request.getClaimedOffset();

            // write request
            writer.write(buffer, writeOffset);
            request.commit();
        }
        catch (Exception e)
        {
            request.abort();

            throw new RuntimeException(e);
        }
    }

    public void awaitResponse(BufferReader reader)
    {
        try
        {
            request.awaitResponse();

            final DirectBuffer responseBuffer = request.getResponseBuffer();
            final int responseLength = request.getResponseLength();

            final UnsafeBuffer responseBufCopy = new UnsafeBuffer(new byte[responseLength]);
            responseBuffer.getBytes(0, responseBufCopy, 0, responseLength);

            reader.wrap(responseBuffer, 0, responseLength);
        }
        finally
        {
            try
            {
                request.close();
            }
            finally
            {
                connection.close();
            }
        }
    }



}
