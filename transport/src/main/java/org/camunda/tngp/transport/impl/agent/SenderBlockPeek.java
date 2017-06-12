package org.camunda.tngp.transport.impl.agent;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.impl.ChannelImpl;

public class SenderBlockPeek extends BlockPeek
{
    protected final UnsafeBuffer sendErrorBlock = new UnsafeBuffer(0, 0);

    private static final int MAX_BLOCK_SIZE = 1024 * 1024;

    protected ChannelImpl currentChannel;
    protected boolean hasAvailable = false;

    public int peek(Subscription senderSubscription, Int2ObjectHashMap<ChannelImpl> channelMap)
    {
        int available = 0;

        if (!hasAvailable)
        {
            available = senderSubscription.peekBlock(this, MAX_BLOCK_SIZE, true);

            if (available > 0)
            {
                final int channelId = getStreamId();

                currentChannel = channelMap.get(channelId);

                if (currentChannel == null || !currentChannel.isReady())
                {
                    markFailed();
                    System.err.println("Channel with id " + channelId + " not open.");
                }
                else
                {
                    hasAvailable = true;
                }
            }
        }

        return available;
    }

    public boolean canSend()
    {
        return hasAvailable && byteBuffer.hasRemaining();
    }


    public int doSend()
    {
        int bytesSent = 0;

        if (hasAvailable)
        {
            bytesSent = currentChannel.write(byteBuffer);

            if (!byteBuffer.hasRemaining())
            {
                markCompleted();
                hasAvailable = false;
                currentChannel = null;
            }
            else if (bytesSent == -1)
            {
                try
                {
                    // TODO: only wrap incomplete messages
                    sendErrorBlock.wrap(byteBuffer, bufferOffset, bufferOffset + blockLength);

                    currentChannel.onChannelSendError(sendErrorBlock, 0, getBlockLength());
                }
                finally
                {
                    hasAvailable = false;
                    currentChannel = null;
                    markFailed();
                }
            }
        }

        return bytesSent;
    }

    public void onChannelRemoved(ChannelImpl c)
    {
        if (currentChannel == c)
        {
            markFailed();
            hasAvailable = false;
            currentChannel = null;
        }
    }

}
