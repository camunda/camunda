 package org.camunda.tngp.transport.impl.agent;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.impl.TransportChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;
import org.camunda.tngp.transport.spi.TransportChannelHandler;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class Sender implements Agent, Consumer<SenderCmd>, BlockHandler
{
    protected final ManyToOneConcurrentArrayQueue<SenderCmd> cmdQueue;

    protected final Int2ObjectHashMap<TransportChannelImpl> channelMap;

    protected final List<TransportChannelImpl> channelsWithControlFrames;

    protected final Dispatcher sendBuffer;

    protected TransportChannelHandler channelHandler;

    protected final UnsafeBuffer sendErrorBlock = new UnsafeBuffer(0,0);

    public Sender(TransportContext context)
    {
        cmdQueue = context.getSenderCmdQueue();
        sendBuffer = context.getSendBuffer();
        channelMap = new Int2ObjectHashMap<>();
        channelsWithControlFrames = new ArrayList<>(10);
    }

    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

        workCount += sendControlFrames();

        workCount += sendBuffer.pollBlock(this, Integer.MAX_VALUE, true);

        return workCount;
    }

    protected int sendControlFrames()
    {
        int workCount = 0;

        for (TransportChannelImpl channel : channelsWithControlFrames)
        {
            channel.writeControlFrame();
        }

        channelsWithControlFrames.clear();

        return workCount;
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
        channelsWithControlFrames.remove(c);
    }

    @Override
    public void onBlockAvailable(
            final ByteBuffer buffer,
            final int blockOffset,
            final int blockLength,
            final int channelId,
            final long position)
    {
        boolean blockWritten = false;

        final TransportChannelImpl channel = channelMap.get(channelId);

        buffer.limit(blockOffset + blockLength);
        buffer.position(blockOffset);

        blockWritten = channel.writeMessage(buffer, position);

        if(!blockWritten)
        {
            sendErrorBlock.wrap(buffer);

            channel.getChannelHandler()
                .onChannelSendError(channel, sendErrorBlock, 0, blockLength);
        }
    }

    public void sendControlFrame(TransportChannelImpl channel)
    {
        channelsWithControlFrames.add(channel);
    }

}
