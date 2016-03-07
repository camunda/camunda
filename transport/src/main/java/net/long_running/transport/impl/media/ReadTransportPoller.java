package net.long_running.transport.impl.media;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import net.long_running.transport.impl.BaseChannelImpl;
import net.long_running.transport.impl.BaseChannelImpl.State;
import net.long_running.transport.impl.agent.Receiver;
import net.long_running.transport.impl.agent.ReceiverCmd;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.nio.TransportPoller;

public class ReadTransportPoller extends TransportPoller
{
    public final static int ITERATION_THRESHHOLD = 5;

    protected final List<BaseChannelImpl> channels = new ArrayList<>(100);

    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> cmdQueue;

    public ReadTransportPoller(Receiver receiver)
    {
        this.cmdQueue = receiver.getCmdQueue();
    }

    public int pollNow()
    {
        int workCount = 0;

        if(channels.size() <= ITERATION_THRESHHOLD)
        {
            for (BaseChannelImpl channel : channels)
            {
                workCount += channelReceive(channel);
            }
        }
        else
        {
            try
            {
                selector.selectNow();
                workCount = selectedKeySet.forEach(processKeyFn);
            }
            catch (IOException e)
            {
                selectedKeySet.reset();
                LangUtil.rethrowUnchecked(e);
            }
        }

        return workCount;
    }

    protected int processKey(SelectionKey key) {

        if(key != null)
        {
            return channelReceive((BaseChannelImpl) key.attachment());
        }

        return 0;
    }

    protected int channelReceive(final BaseChannelImpl channel)
    {
        int received = channel.receive();

        if(channel.getState() == State.CLOSING)
        {
            cmdQueue.add((r) -> {
               r.onChannelClose(channel);
            });
        }

        return received;
    }

    public void addChannel(BaseChannelImpl channel)
    {
        channel.registerSelector(selector, SelectionKey.OP_READ);
        channels.add(channel);
    }

    public void removeChannel(BaseChannelImpl channel)
    {
        channel.removeSelector(selector);
        channels.remove(channel);
    }

}
