package io.zeebe.transport.impl.media;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.function.ToIntFunction;

import org.agrona.LangUtil;
import org.agrona.nio.TransportPoller;
import io.zeebe.transport.impl.ChannelImpl;
import io.zeebe.transport.impl.ServerSocketBindingImpl;
import io.zeebe.transport.impl.agent.Conductor;

public class AcceptTransportPoller extends TransportPoller
{
    protected final ToIntFunction<SelectionKey> processKeyFn = this::processKey;
    protected final Conductor conductor;

    protected int bindingCount = 0;

    public AcceptTransportPoller(Conductor conductor)
    {
        this.conductor = conductor;
    }

    public int pollNow()
    {
        int workCount = 0;

        if (bindingCount > 0 && selector.isOpen())
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
            final ServerSocketBindingImpl serverSocketBinding = (ServerSocketBindingImpl) key.attachment();
            final SocketChannel serverChannel = serverSocketBinding.accept();

            final ChannelImpl channel = conductor.newChannel(
                    serverChannel,
                    serverSocketBinding.getChannelHandler(),
                    serverSocketBinding.getChannelLifecycle());

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
