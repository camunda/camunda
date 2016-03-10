package org.camunda.tngp.transport;

import static net.long_running.dispatcher.AsyncCompletionCallback.completeFuture;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.camunda.tngp.transport.impl.ClientChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;

import net.long_running.dispatcher.AsyncCompletionCallback;
import uk.co.real_logic.agrona.LangUtil;

public class ClientChannelBuilder
{

    protected final TransportContext transportContext;

    protected final InetSocketAddress remoteAddress;

    protected ChannelFrameHandler channelFrameHandler = ChannelFrameHandler.DISCARD_HANDLER;

    protected ChannelErrorHandler channelErrorHandler = ChannelErrorHandler.DEFAULT_ERROR_HANDLER;

    public ClientChannelBuilder(TransportContext transportContext, InetSocketAddress remoteAddress)
    {
        this.transportContext = transportContext;
        this.remoteAddress = remoteAddress;
    }

    public ClientChannelBuilder channelFrameHandler(ChannelFrameHandler frameHandler)
    {
        this.channelFrameHandler = frameHandler;
        return this;
    }

    public ClientChannelBuilder channelErrorHandler(ChannelErrorHandler errorHandler)
    {
        this.channelErrorHandler = errorHandler;
        return this;
    }

    public void connect(AsyncCompletionCallback<ClientChannel> completionCallback)
    {
        new ClientChannelImpl(transportContext, channelFrameHandler, channelErrorHandler, remoteAddress, completionCallback);
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
