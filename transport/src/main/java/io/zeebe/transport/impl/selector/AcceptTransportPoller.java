package io.zeebe.transport.impl.selector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.ToIntFunction;

import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;

import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.transport.impl.actor.ServerActorContext;

public class AcceptTransportPoller extends TransportPoller
{
    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;
    private final ServerActorContext actorContext;

    public AcceptTransportPoller(ServerActorContext actorContext)
    {
        this.actorContext = actorContext;
    }

    public int pollNow()
    {
        int workCount = 0;

        if (selector.isOpen())
        {
            try
            {
                selector.selectNow();
                if (!selectedKeySet.isEmpty())
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

    protected int processKey(SelectionKey key)
    {

        if (key != null)
        {
            final ServerSocketBinding serverSocketBinding = (ServerSocketBinding) key.attachment();
            final SocketChannel serverChannel = serverSocketBinding.accept();

            actorContext.onServerChannelOpened(serverChannel);

            return 1;
        }

        return 0;
    }

    public void addServerSocketBinding(ServerSocketBinding binding)
    {
        binding.registerSelector(selector, SelectionKey.OP_ACCEPT);
    }
}
