package net.long_running.transport.impl.agent;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.long_running.dispatcher.BlockHandler;
import net.long_running.dispatcher.Dispatcher;
import net.long_running.transport.impl.BaseChannelImpl;
import net.long_running.transport.impl.TransportContext;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class Sender implements Agent, Consumer<SenderCmd>, BlockHandler
{

    protected final ManyToOneConcurrentArrayQueue<SenderCmd> cmdQueue;

    protected final Int2ObjectHashMap<BaseChannelImpl> channelMap;

    protected final List<BaseChannelImpl> channelsWithControlFrames;

    protected final Dispatcher sendBuffer;

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

        workCount += sendBuffer.pollBlock(this, 1, true);

        workCount += sendControlFrames();

        return workCount;
    }

    protected int sendControlFrames()
    {
        int workCount = 0;

        for (BaseChannelImpl channel : channelsWithControlFrames)
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

    public void registerChannel(BaseChannelImpl c)
    {
        channelMap.put(c.getId(), c);
    }

    public void removeChannel(BaseChannelImpl c)
    {
        channelMap.remove(c.getId());
        channelsWithControlFrames.remove(c);
    }

    @Override
    public void onBlockAvailable(
            final ByteBuffer buffer,
            final int blockOffset,
            final int blockLength,
            final int streamId,
            final long position)
    {
        final BaseChannelImpl channel = channelMap.get(streamId);

        if(channel != null)
        {
            buffer.limit(blockOffset + blockLength);
            buffer.position(blockOffset);
            channel.writeMessage(buffer, position);
        }
        else
        {
            // TODO
            throw new RuntimeException("Cannot write to channel with id "+streamId + " channel not registered with sender");
        }
    }

    public void sendControlFrame(BaseChannelImpl channel)
    {
        channelsWithControlFrames.add(channel);
    }

}
