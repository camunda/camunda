package org.camunda.tngp.transport.requestresponse.server;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;

import uk.co.real_logic.agrona.DirectBuffer;

public class DeferredResponse
{
    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected long asyncOperationId;
    protected ResponseCompletionHandler completionHandler;
    protected Object attachement;

    public DeferredResponse(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
    }

    public boolean allocate(int channelId, int msgLength)
    {
        this.asyncOperationId = -1;
        long claimedPosition = -1;

        do
        {
            claimedPosition = sendBuffer.claim(claimedFragment, msgLength, channelId);
        }
        while(claimedPosition == -2);

        return claimedPosition >= 0;
    }

    public DeferredResponse defer(final long asyncOperationId, ResponseCompletionHandler handler, Object attachement)
    {
        this.asyncOperationId = asyncOperationId;
        this.completionHandler = handler;
        this.attachement = attachement;
        return this;
    }

    public void resolve(DirectBuffer asyncWorkBuffer, int offset, int length)
    {
        if(claimedFragment.isOpen() && isDeferred())
        {
            completionHandler.onAsyncWorkCompleted(this, asyncWorkBuffer, offset, length, attachement);
            this.asyncOperationId = -1;
            this.completionHandler = null;
            this.attachement = null;
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

    public ClaimedFragment getClaimedFragment()
    {
        return claimedFragment;
    }

}
