package net.long_running.transport.impl.media;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import net.long_running.transport.impl.BaseChannelImpl;
import net.long_running.transport.impl.agent.Receiver;
import net.long_running.transport.impl.agent.ReceiverCmd;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.nio.TransportPoller;

public class ReadTransportPoller extends TransportPoller
{
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

        if(channels.size() <= ITERATION_THRESHOLD)
        {
            for (BaseChannelImpl channel : channels)
            {
                workCount += channel.receive();
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

        int workCount = 0;

        if(key != null && key.isReadable())
        {
            final BaseChannelImpl channel = (BaseChannelImpl) key.attachment();

            workCount = channel.receive();
        }

        return workCount;
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
