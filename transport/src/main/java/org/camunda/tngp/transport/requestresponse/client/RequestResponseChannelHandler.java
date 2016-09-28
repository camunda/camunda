package org.camunda.tngp.transport.requestresponse.client;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor.connectionIdOffset;
import static org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor.requestIdOffset;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

/**
 * A client for the request/response protocol
 */
public class RequestResponseChannelHandler implements TransportChannelHandler
{
    public static final short PROTOCOL_ID = 1;

    protected final TransportConnectionPoolImpl connectionManager;

    public RequestResponseChannelHandler(final TransportConnectionPoolImpl connectionManager)
    {
        this.connectionManager = connectionManager;
    }

    @Override
    public void onChannelOpened(TransportChannel transportChannel)
    {
    }


    @Override
    public void onChannelClosed(TransportChannel transportChannel)
    {
        connectionManager.handleChannelClose(transportChannel);
    }

    @Override
    public void onChannelSendError(
        final TransportChannel transportChannel,
        final DirectBuffer blockBuffer,
        final int blockOffset,
        final int blockLength)
    {
        int scanOffset = blockOffset;

        do
        {
            final int dataFragmentLength = blockBuffer.getInt(lengthOffset(scanOffset));
            final int messageOffset = messageOffset(scanOffset);
            final long connectionId = blockBuffer.getLong(connectionIdOffset(messageOffset));
            final long requestId = blockBuffer.getLong(requestIdOffset(messageOffset));

            final TransportConnectionImpl connection = connectionManager.findConnection(connectionId);

            boolean isHandled = false;

            try
            {
                if (connection != null)
                {
                    isHandled = connection.processSendError(requestId);
                }

                if (!isHandled)
                {
                    System.err.println("Unhandled error of request " + requestId + " on connection " + connectionId);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            scanOffset += alignedLength(dataFragmentLength);
        }
        while (scanOffset < blockLength);
    }

    @Override
    public boolean onChannelReceive(
        final TransportChannel channel,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        final int requestResponseHeaderOffset = offset + TransportHeaderDescriptor.headerLength();
        final int requestResponseMessageLength = length - TransportHeaderDescriptor.headerLength();

        final long connectionId = buffer.getLong(connectionIdOffset(requestResponseHeaderOffset));
        final TransportConnectionImpl connection = connectionManager.findConnection(connectionId);

        boolean isHandled = false;

        if (connection != null)
        {
            isHandled = connection.processResponse(buffer, requestResponseHeaderOffset, requestResponseMessageLength);
        }

        if (!isHandled)
        {
            System.err.println("Dropping protocol frame on connection " + connectionId);
        }

        return isHandled;
    }

    @Override
    public void onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        // this protocol does not send any control frames from server to client.
    }

}
