package io.zeebe.transport.impl.selector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;

import io.zeebe.transport.impl.TransportChannel;

public class ReadTransportPoller extends TransportPoller
{
    protected final List<TransportChannel> channels = new ArrayList<>();

    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

    public int pollNow()
    {
        int workCount = 0;

        if (channels.size() <= ITERATION_THRESHOLD)
        {
            for (int i = 0; i < channels.size(); i++)
            {
                final TransportChannel channel = channels.get(i);
                workCount += channel.receive();
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
            final TransportChannel channel = (TransportChannel) key.attachment();
            workCount = channel.receive();
        }

        return workCount;
    }

    public void addChannel(TransportChannel channel)
    {
        try
        {
            channel.registerSelector(selector, SelectionKey.OP_READ);
            channels.add(channel);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void removeChannel(TransportChannel channel)
    {
        channels.remove(channel);
    }

}
