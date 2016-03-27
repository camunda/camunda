package org.camunda.tngp.transport.protocol.server;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;

import uk.co.real_logic.agrona.DirectBuffer;

public class DeferredMessage
{
    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected long completionPosition;
    protected AsyncRequestHandler handler;

    public DeferredMessage(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public boolean allocate(
            int channelId,
            int msgLength)
    {
        this.completionPosition = -1;
        long claimedPosition = -1;

        do
        {
            claimedPosition = sendBuffer.claim(claimedFragment, msgLength, channelId);
        }
        while(claimedPosition == -2);

        return claimedPosition >= 0;
    }

    public DeferredMessage defer(final AsyncRequestHandler handler, final long asyncOperationPosition)
    {
        this.completionPosition = asyncOperationPosition;
        this.handler = handler;
        return this;
    }

    public void resolve(DirectBuffer asyncWorkBuffer, int offset, int length)
    {
        if(claimedFragment.isOpen() && isDeferred())
        {
            handler.onAsyncWorkCompleted(this, asyncWorkBuffer, offset, length);
            this.completionPosition = -1;
            handler = null;
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
            //claimedFragment.abort();
        }
    }

    public boolean isDeferred()
    {
        return completionPosition >= 0;
    }

    public ClaimedFragment getClaimedFragment()
    {
        return claimedFragment;
    }

}
