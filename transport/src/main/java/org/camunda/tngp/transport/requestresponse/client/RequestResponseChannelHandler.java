package org.camunda.tngp.transport.requestresponse.client;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.TYPE_PROTO_CONTROL_FRAME;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.*;

import java.nio.ByteBuffer;

import org.camunda.tngp.transport.TransportChannel;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

/**
 * A client for the request/response protocol
 */
public class RequestResponseChannelHandler implements TransportChannelHandler
{
    public static final short PROTOCOL_ID = 1;

    public final ByteBuffer upgradeFrame;

    protected final TransportConnectionPoolImpl connectionManager;

    public RequestResponseChannelHandler(final TransportConnectionPoolImpl connectionManager)
    {
        this.connectionManager = connectionManager;

        upgradeFrame = ByteBuffer.allocate(alignedLength(BitUtil.SIZE_OF_SHORT));
        final UnsafeBuffer ctrMsgWriter = new UnsafeBuffer(0, 0);
        ctrMsgWriter.wrap(upgradeFrame);
        ctrMsgWriter.putInt(lengthOffset(0), BitUtil.SIZE_OF_SHORT);
        ctrMsgWriter.putShort(typeOffset(0), TYPE_PROTO_CONTROL_FRAME);
        ctrMsgWriter.putShort(messageOffset(0), PROTOCOL_ID);
    }

    @Override
    public void onChannelOpened(TransportChannel transportChannel)
    {
        transportChannel.sendControlFrame(upgradeFrame);
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
        final long connectionId = buffer.getLong(connectionIdOffset(offset));
        final TransportConnectionImpl connection = connectionManager.findConnection(connectionId);

        boolean isHandled = false;

        if (connection != null)
        {
            isHandled = connection.processResponse(buffer, offset, length);
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
