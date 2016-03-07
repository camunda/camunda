package net.long_running.transport.impl.agent;

import java.nio.ByteBuffer;
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

    protected final Dispatcher sendBuffer;

    public Sender(TransportContext context)
    {
        cmdQueue = context.getSenderCmdQueue();
        sendBuffer = context.getSendBuffer();
        channelMap = new Int2ObjectHashMap<>();
    }

    public int doWork() throws Exception
    {
        int work = 0;

        work += cmdQueue.drain(this);

        work += sendBuffer.pollBlock(this, 5, true);

        return work;
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
    }

    @Override
    public void onBlockAvailable(ByteBuffer buffer, int blockOffset, int blockLength, int streamId)
    {
        final BaseChannelImpl channel = channelMap.get(streamId);

        buffer.limit(blockOffset + blockLength);
        buffer.position(blockOffset);

        channel.write(buffer);
    }

}
