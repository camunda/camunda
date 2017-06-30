package io.zeebe.transport.impl.media;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;
import io.zeebe.transport.impl.ChannelImpl;

public class ReadTransportPoller extends TransportPoller
{
    protected final List<ChannelImpl> channels = new ArrayList<>(100);

    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

    public int pollNow()
    {
        int workCount = 0;

        if (channels.size() <= ITERATION_THRESHOLD)
        {
            for (int i = 0; i < channels.size(); i++)
            {
                final ChannelImpl channel = channels.get(i);
                if (channel.isConnected())
                {
                    workCount += channel.receive();
                }
            }
        }
        else
        {
            if (selector.isOpen())
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
        }

        return workCount;
    }

    protected int processKey(SelectionKey key)
    {
        int workCount = 0;

        if (key != null && key.isReadable())
        {
            final ChannelImpl channel = (ChannelImpl) key.attachment();
            workCount = channel.receive();
        }

        return workCount;
    }

    public void addChannel(ChannelImpl channel)
    {
        channel.registerSelector(selector, SelectionKey.OP_READ);
        channels.add(channel);
    }

    public void removeChannel(ChannelImpl channel)
    {
        channel.removeSelector(selector);
        channels.remove(channel);
    }

}
