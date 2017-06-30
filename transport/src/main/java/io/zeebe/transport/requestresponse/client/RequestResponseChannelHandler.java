package io.zeebe.transport.requestresponse.client;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.transport.requestresponse.RequestResponseProtocolHeaderDescriptor.connectionIdOffset;
import static io.zeebe.transport.requestresponse.RequestResponseProtocolHeaderDescriptor.requestIdOffset;

import org.agrona.DirectBuffer;
import io.zeebe.transport.Channel;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.transport.spi.TransportChannelHandler;

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
    public void onChannelOpened(Channel transportChannel)
    {
    }

    @Override
    public void onChannelInterrupted(Channel transportChannel)
    {
        onChannelClosed(transportChannel);
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
        connectionManager.handleChannelClose(transportChannel);
    }

    @Override
    public void onChannelSendError(
        final Channel transportChannel,
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
        final Channel channel,
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        final int requestResponseHeaderOffset = offset + TransportHeaderDescriptor.headerLength();
        final int requestResponseMessageLength = length - TransportHeaderDescriptor.headerLength();

        final long connectionId = buffer.getLong(connectionIdOffset(requestResponseHeaderOffset));
        final TransportConnectionImpl connection = connectionManager.findConnection(connectionId);

        if (connection != null)
        {
            if (!connection.processResponse(buffer, requestResponseHeaderOffset, requestResponseMessageLength))
            {
                System.err.println("Dropping protocol frame on connection " + connectionId);
            }
        }

        return true;
    }

    @Override
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        // this protocol does not send any control frames from server to client.
        return true;
    }

}
