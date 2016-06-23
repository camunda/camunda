package org.camunda.tngp.transport.impl.media;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.function.ToIntFunction;

import org.camunda.tngp.transport.impl.ClientChannelImpl;
import org.camunda.tngp.transport.impl.agent.TransportConductor;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.nio.TransportPoller;

public class ConnectTransportPoller extends TransportPoller
{
    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> cmdQueue;

    protected int channelCount = 0;

    public ConnectTransportPoller(TransportConductor receiver)
    {
        this.cmdQueue = receiver.getCmdQueue();
    }

    public int pollNow()
    {
        int workCount = 0;

        if (channelCount > 0)
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
            final ClientChannelImpl channel = (ClientChannelImpl) key.attachment();
            channel.finishConnect();

            cmdQueue.add((cc) ->
            {
                cc.onClientChannelConnected(channel);
            });

            return 1;
        }

        return 0;
    }

    public void addChannel(ClientChannelImpl channel)
    {
        channel.registerSelector(selector, SelectionKey.OP_CONNECT);
        ++channelCount;
    }

    public void removeChannel(ClientChannelImpl channel)
    {
        channel.removeSelector(selector);
        --channelCount;
    }

}
