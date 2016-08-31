package org.camunda.tngp.transport.requestresponse.server;

import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.connectionIdOffset;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.framedLength;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.headerLength;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.requestIdOffset;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool.DeferredResponseControl;
import org.camunda.tngp.util.buffer.BufferWriter;


public class DeferredResponse
{
    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected int channelId;
    protected long connectionId;
    protected long requestId;

    protected DeferredResponseControl responseControl;

    protected boolean isDeferred = false;

    public DeferredResponse(Dispatcher sendBuffer, DeferredResponseControl deferredResponseControl)
    {
        this.sendBuffer = sendBuffer;
        this.responseControl = deferredResponseControl;
    }

    public void reset()
    {
        this.channelId = -1;
        this.connectionId = -1;
        this.requestId = -1;
        this.isDeferred = false;
    }

    public void open(int channelId, long connectionId, long requestId)
    {
        this.channelId = channelId;
        this.connectionId = connectionId;
        this.requestId = requestId;
    }

    public boolean allocate(int msgLength)
    {
        final int framedLength = framedLength(msgLength);
        long claimedPosition = -1;

        do
        {
            claimedPosition = sendBuffer.claim(claimedFragment, framedLength, channelId);
        }
        while (claimedPosition == -2);

        final boolean isAllocated = claimedPosition >= 0;

        if (isAllocated)
        {
            // write header
            final MutableDirectBuffer buffer = claimedFragment.getBuffer();
            final int claimedOffset = claimedFragment.getOffset();

            buffer.putLong(connectionIdOffset(claimedOffset), connectionId);
            buffer.putLong(requestIdOffset(claimedOffset), requestId);
        }

        return isAllocated;
    }

    public boolean allocateAndWrite(final BufferWriter writer)
    {
        final int length = writer.getLength();

        final boolean isAllocated = allocate(length);

        if (isAllocated)
        {
            final MutableDirectBuffer writeBuffer = getBuffer();
            final int claimedOffset = getClaimedOffset();

            writer.write(writeBuffer, claimedOffset);
        }

        return isAllocated;
    }

    public int defer()
    {
        final int result = 1;
        responseControl.defer(this);
        isDeferred = true;

        return result;
    }

    public void resolve(DirectBuffer asyncWorkBuffer, int offset, int length, long blockPosition)
    {
        if (claimedFragment.isOpen() && isDeferred())
        {
            commit();
        }
    }

    public void commit()
    {
        if (claimedFragment.isOpen())
        {
            claimedFragment.commit();
            responseControl.reclaim(this);
        }
    }

    public void abort()
    {
        if (claimedFragment.isOpen())
        {
            claimedFragment.abort();
        }
    }

    public boolean isDeferred()
    {
        return isDeferred;
    }

    public MutableDirectBuffer getBuffer()
    {
        return claimedFragment.getBuffer();
    }

    public int getClaimedOffset()
    {
        return claimedFragment.getOffset() + headerLength();
    }

}
