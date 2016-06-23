package org.camunda.tngp.transport.impl.agent;

import org.camunda.tngp.dispatcher.BlockPeek;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.camunda.tngp.transport.impl.TransportChannelImpl;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class SenderBlockPeek extends BlockPeek
{
    protected final UnsafeBuffer sendErrorBlock = new UnsafeBuffer(0, 0);

    private static final int MAX_BLOCK_SIZE = 1024 * 1024;

    protected TransportChannelImpl currentChannel;
    protected boolean hasAvailable = false;

    public int peek(Subscription senderSubscription, Int2ObjectHashMap<TransportChannelImpl> channelMap)
    {
        int available = 0;

        if (!hasAvailable)
        {
            available = senderSubscription.peekBlock(this, MAX_BLOCK_SIZE, true);

            if (available > 0)
            {
                final int channelId = getStreamId();

                currentChannel = channelMap.get(channelId);

                if (currentChannel == null)
                {
                    markFailed();
                    System.err.println("Cannel with id " + channelId + " not open.");
                }
                else
                {
                    hasAvailable = true;
                }
            }
        }

        return available;
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

                    currentChannel.getChannelHandler()
                        .onChannelSendError(currentChannel, sendErrorBlock, 0, getBlockLength());
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

    public void onChannelRemoved(TransportChannelImpl c)
    {
        if (currentChannel == c)
        {
            markFailed();
            hasAvailable = false;
            currentChannel = null;
        }
    }

}
