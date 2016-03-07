package net.long_running.transport;

import static net.long_running.dispatcher.AsyncCompletionCallback.completeFuture;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import net.long_running.dispatcher.AsyncCompletionCallback;
import net.long_running.transport.impl.ClientChannelImpl;
import net.long_running.transport.impl.TransportContext;
import uk.co.real_logic.agrona.LangUtil;

public class ClientChannelBuilder
{

    protected final TransportContext transportContext;

    protected final InetSocketAddress remoteAddress;

    protected ChannelFrameHandler channelFrameHandler = ChannelFrameHandler.DISCARD_HANDLER;

    public ClientChannelBuilder(TransportContext transportContext, InetSocketAddress remoteAddress)
    {
        this.transportContext = transportContext;
        this.remoteAddress = remoteAddress;
    }

    public ClientChannelBuilder channelFrameHandler(ChannelFrameHandler channelReader)
    {
        this.channelFrameHandler = channelReader;
        return this;
    }

    public void connect(AsyncCompletionCallback<ClientChannel> completionCallback)
    {
        new ClientChannelImpl(channelFrameHandler, transportContext, remoteAddress, completionCallback);
    }

    public ClientChannel connectSync()
    {
        final CompletableFuture<ClientChannel> future = new CompletableFuture<>();

        connect(completeFuture(future));

        try
        {
            return future.get();
        }
        catch (InterruptedException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
        catch (ExecutionException e)
        {
            LangUtil.rethrowUnchecked((Exception) e.getCause());
        }

        return null;
    }

}
