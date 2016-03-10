package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.camunda.tngp.transport.impl.ServerSocketBindingImpl;
import org.camunda.tngp.transport.impl.TransportContext;

import static net.long_running.dispatcher.AsyncCompletionCallback.*;

import net.long_running.dispatcher.AsyncCompletionCallback;
import uk.co.real_logic.agrona.LangUtil;

public class ServerSocketBindingBuilder
{

    protected final TransportContext transportContext;

    protected final InetSocketAddress bindAddress;

    protected ServerChannelHandler channelHandler;

    public ServerSocketBindingBuilder serverChannelHandler(ServerChannelHandler channelHandler)
    {
        this.channelHandler = channelHandler;
        return this;
    }

    public ServerSocketBindingBuilder(TransportContext transportContext, InetSocketAddress bindAddress)
    {
        this.transportContext = transportContext;
        this.bindAddress = bindAddress;
    }


    public ServerSocketBinding bindSync()
    {
        final CompletableFuture<ServerSocketBinding> completableFuture = new CompletableFuture<>();

        bind(completeFuture(completableFuture));

        try
        {
            return completableFuture.get();
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

    public void bind(AsyncCompletionCallback<ServerSocketBinding> completionCallback)
    {
        new ServerSocketBindingImpl(transportContext, channelHandler, bindAddress, completionCallback);
    }

}
