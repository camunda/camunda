package io.zeebe.transport.impl.agent;

import java.util.concurrent.CompletableFuture;

import io.zeebe.transport.Loggers;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.impl.ChannelImpl;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.Actor;
import org.slf4j.Logger;

public class Sender implements Actor
{
    public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

    protected final DeferredCommandContext commandContext;

    protected final ManyToOneRingBuffer controlFrameBuffer;
    protected ChannelImpl controlFrameInProgressChannel;

    protected final Int2ObjectHashMap<ChannelImpl> channelMap;

    protected final Subscription senderSubscription;

    protected final SenderBlockPeek blockPeek = new SenderBlockPeek();

    public Sender(
            ManyToOneRingBuffer controlFrameBuffer,
            TransportContext context)
    {
        senderSubscription = context.getSenderSubscription();
        channelMap = new Int2ObjectHashMap<>();
        this.controlFrameBuffer = controlFrameBuffer;
        this.commandContext = new DeferredCommandContext();
    }

    protected boolean isMessageSendingInProgress()
    {
        return blockPeek.canSend();
    }

    protected boolean isControlFrameSendingInProgress()
    {
        return controlFrameInProgressChannel != null;
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += commandContext.doWork();

        if (!isMessageSendingInProgress())
        {
            workCount += sendControlFrames();
        }

        if (!isControlFrameSendingInProgress())
        {
            if (!isMessageSendingInProgress())
            {
                workCount += blockPeek.peek(senderSubscription, channelMap);
            }

            workCount += blockPeek.doSend();
        }

        return workCount;
    }


    public int sendControlFrames()
    {
        int workCount = 0;
        if (isControlFrameSendingInProgress())
        {
            final boolean controlFrameSendComplete = controlFrameInProgressChannel.trySendControlFrame();
            if (controlFrameSendComplete)
            {
                controlFrameInProgressChannel = null;
                workCount++;
            }
        }

        int controlFramesProcessed = -1;
        while (controlFrameInProgressChannel == null && controlFramesProcessed != 0)
        {
            controlFramesProcessed = controlFrameBuffer.read(this::sendControlFrame, 1);
            workCount += controlFramesProcessed;
        }

        return workCount;
    }

    protected void sendControlFrame(int channelId, DirectBuffer buf, int offset, int length)
    {
        final ChannelImpl channelImpl = channelMap.get(channelId);
        if (channelImpl != null)
        {
            channelImpl.initControlFrame(buf, offset, length);
            final boolean controlFrameSendComplete = channelImpl.trySendControlFrame();
            if (!controlFrameSendComplete)
            {
                controlFrameInProgressChannel = channelImpl;
            }
        }
        else
        {
            // ignore control frame
            LOG.error("Sender: Not sending scheduled control frame for channel {}. Channel not registered.", channelId);
        }
    }

    @Override
    public String name()
    {
        return "sender";
    }


    public CompletableFuture<Void> removeChannelAsync(ChannelImpl c)
    {
        return commandContext.runAsync((future) ->
        {
            removeChannel(c);
            future.complete(null);
        });
    }

    protected void removeChannel(ChannelImpl c)
    {
        channelMap.remove(c.getStreamId());
        blockPeek.onChannelRemoved(c);
    }

    public CompletableFuture<Void> registerChannelAsync(ChannelImpl c)
    {
        return commandContext.runAsync((future) ->
        {
            registerChannel(c);
            future.complete(null);
        });
    }

    protected void registerChannel(ChannelImpl c)
    {
        channelMap.put(c.getStreamId(), c);
    }

}
