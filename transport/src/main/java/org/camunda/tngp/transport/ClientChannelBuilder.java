package org.camunda.tngp.transport;

import static org.camunda.tngp.dispatcher.AsyncCompletionCallback.*;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.camunda.tngp.dispatcher.AsyncCompletionCallback;
import org.camunda.tngp.transport.impl.ClientChannelImpl;
import org.camunda.tngp.transport.impl.TransportContext;

import uk.co.real_logic.agrona.LangUtil;

public class ClientChannelBuilder
{

    protected final TransportContext transportContext;

    protected final InetSocketAddress remoteAddress;

    protected ChannelErrorHandler channelErrorHandler = ChannelErrorHandler.DEFAULT_ERROR_HANDLER;

    public ClientChannelBuilder(TransportContext transportContext, InetSocketAddress remoteAddress)
    {
        this.transportContext = transportContext;
        this.remoteAddress = remoteAddress;
    }

    public ClientChannelBuilder channelErrorHandler(ChannelErrorHandler errorHandler)
    {
        this.channelErrorHandler = errorHandler;
        return this;
    }

    public void connect(AsyncCompletionCallback<ClientChannel> completionCallback)
    {
        new ClientChannelImpl(transportContext, channelErrorHandler, remoteAddress, completionCallback);
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
