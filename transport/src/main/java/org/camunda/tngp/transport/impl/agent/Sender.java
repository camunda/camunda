package org.camunda.tngp.transport.impl.agent;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.impl.TransportChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;

public class Sender implements Agent, Consumer<SenderCmd>
{

    protected final ManyToOneConcurrentArrayQueue<SenderCmd> cmdQueue;

    protected final ManyToOneRingBuffer controlFrameBuffer;
    protected TransportChannelImpl controlFrameInProgressChannel;

    protected final Int2ObjectHashMap<TransportChannelImpl> channelMap;

    protected final Subscription senderSubscription;

    protected final SenderBlockPeek blockPeek = new SenderBlockPeek();

    public Sender(TransportContext context)
    {
        cmdQueue = context.getSenderCmdQueue();
        senderSubscription = context.getSenderSubscription();
        channelMap = new Int2ObjectHashMap<>();
        this.controlFrameBuffer = context.getControlFrameBuffer();
    }

    protected boolean isMessageSendingInProgress()
    {
        return blockPeek.canSend();
    }

    protected boolean isControlFrameSendingInProgress()
    {
        return controlFrameInProgressChannel != null;
    }

    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

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
        final TransportChannelImpl channelImpl = channelMap.get(channelId);
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
            System.err.println("Sender: Not sending scheduled control frame for channel " + channelId + ". Channel not registered.");
        }
    }

    public String roleName()
    {
        return "sender";
    }

    @Override
    public void accept(SenderCmd t)
    {
        t.execute(this);
    }

    public void registerChannel(TransportChannelImpl c, CompletableFuture<Void> future)
    {
        channelMap.put(c.getId(), c);
        future.complete(null);
    }

    public void removeChannel(TransportChannelImpl c)
    {
        channelMap.remove(c.getId());
        blockPeek.onChannelRemoved(c);
    }

    public boolean scheduleControlFrameRequest(TransportChannelImpl channel, DirectBuffer buffer, int offset, int length)
    {
        return controlFrameBuffer.write(channel.getId(), buffer, offset, length);
    }

}
