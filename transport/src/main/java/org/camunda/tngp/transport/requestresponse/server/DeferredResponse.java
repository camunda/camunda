package org.camunda.tngp.transport.requestresponse.server;

import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.*;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentWriter;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class DeferredResponse
{
    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected int channelId;
    protected long connectionId;
    protected long requestId;

    protected long asyncOperationId;
    protected ResponseCompletionHandler completionHandler;
    protected Object attachement;

    public DeferredResponse(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public void reset()
    {
        this.channelId = -1;
        this.connectionId = -1;
        this.requestId = -1;
        this.asyncOperationId = -1;
        this.completionHandler = null;
        this.attachement = null;
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
        while(claimedPosition == -2);

        final boolean isAllocated = claimedPosition >= 0;

        if(isAllocated)
        {
            // write header
            final MutableDirectBuffer buffer = claimedFragment.getBuffer();
            final int claimedOffset = claimedFragment.getOffset();

            buffer.putLong(connectionIdOffset(claimedOffset), connectionId);
            buffer.putLong(requestIdOffset(claimedOffset), requestId);
        }

        return isAllocated;
    }

    public boolean allocateAndWrite(final FragmentWriter writer)
    {
        final int length = writer.getLength();

        boolean isAllocated = allocate(length);

        if(isAllocated)
        {
            final MutableDirectBuffer writeBuffer = getBuffer();
            final int claimedOffset = getClaimedOffset();

            writer.write(writeBuffer, claimedOffset);
        }

        return isAllocated;
    }

    public int defer(final long asyncOperationId, ResponseCompletionHandler handler, Object attachement)
    {
        int result = -1;

        if(asyncOperationId >= 0)
        {
            this.asyncOperationId = asyncOperationId;
            this.completionHandler = handler;
            this.attachement = attachement;
            result = 1;
        }
        else
        {
            abort();
        }

        return result;
    }

    public void resolve(DirectBuffer asyncWorkBuffer, int offset, int length, long blockPosition)
    {
        if(claimedFragment.isOpen() && isDeferred())
        {
            completionHandler.onAsyncWorkCompleted(this, asyncWorkBuffer, offset, length, attachement, blockPosition);
        }
    }

    public void commit()
    {
        if(claimedFragment.isOpen())
        {
            claimedFragment.commit();
        }
    }

    public void abort()
    {
        if(claimedFragment.isOpen())
        {
            claimedFragment.abort();
        }
    }

    public boolean isDeferred()
    {
        return completionHandler != null;
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
