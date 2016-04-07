package org.camunda.tngp.transport.impl.media;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.function.ToIntFunction;

import org.camunda.tngp.transport.impl.ServerChannelImpl;
import org.camunda.tngp.transport.impl.ServerSocketBindingImpl;
import org.camunda.tngp.transport.impl.agent.TransportConductor;
import org.camunda.tngp.transport.impl.agent.TransportConductorCmd;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import uk.co.real_logic.agrona.nio.TransportPoller;

public class AcceptTransportPoller extends TransportPoller
{
    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;

    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> cmdQueue;

    protected int bindingCount = 0;

    public AcceptTransportPoller(TransportConductor conductor)
    {
        this.cmdQueue = conductor.getCmdQueue();
    }

    public int pollNow()
    {
        int workCount = 0;

        if(bindingCount > 0)
        {
            try
            {
                selector.selectNow();
                if(!selectedKeySet.isEmpty())
                {
                    workCount = selectedKeySet.forEach(processKeyFn);
                }
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
            final ServerSocketBindingImpl serverSocketBinding = (ServerSocketBindingImpl) key.attachment();
            final ServerChannelImpl serverChannel = serverSocketBinding.accept();

            cmdQueue.add((cc) ->
            {
                cc.onServerChannelOpened(serverChannel);
            });

            return 1;
        }

        return 0;
    }

    public void addServerSocketBinding(ServerSocketBindingImpl binding)
    {
        binding.registerSelector(selector, SelectionKey.OP_ACCEPT);
        ++bindingCount;
    }

    public void removeServerSocketBinding(ServerSocketBindingImpl binding)
    {
        binding.removeSelector(selector);
        --bindingCount;
    }

}
