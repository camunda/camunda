package io.zeebe.transport.impl.selector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.function.ToIntFunction;

import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;

import io.zeebe.transport.impl.TransportChannel;

public class ConnectTransportPoller extends TransportPoller
{
    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

    protected int channelCount = 0;

    public int pollNow()
    {
        int workCount = 0;

        if (channelCount > 0 && selector.isOpen())
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

    protected int processKey(SelectionKey key)
    {
        if (key != null)
        {
            final TransportChannel channel = (TransportChannel) key.attachment();
            removeChannel(channel);
            channel.finishConnect();

            return 1;
        }

        return 0;
    }

    public void addChannel(TransportChannel channel)
    {
        channel.registerSelector(selector, SelectionKey.OP_CONNECT);
        ++channelCount;
    }

    public void removeChannel(TransportChannel channel)
    {
        channel.removeSelector(selector);
        --channelCount;
    }

}
