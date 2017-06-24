package io.zeebe.transport;

import java.util.concurrent.CompletableFuture;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.transport.impl.RemoteAddressList;
import io.zeebe.transport.impl.ServerReceiveHandler;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;

public class BufferingServerTransport extends ServerTransport
{
    protected final Dispatcher receiveBuffer;

    public BufferingServerTransport(ActorContext transportActorContext, TransportContext transportContext)
    {
        super(transportActorContext, transportContext);
        receiveBuffer = transportContext.getReceiveBuffer();
    }

    public CompletableFuture<ServerInputSubscription> openSubscription(String subscriptionName, ServerMessageHandler messageHandler, ServerRequestHandler requestHandler)
    {
        return receiveBuffer.openSubscriptionAsync(subscriptionName)
            .thenApply(s -> new ServerInputSubscriptionImpl(output, s, transportContext.getRemoteAddressList(), messageHandler, requestHandler));
    }

    protected static class ServerInputSubscriptionImpl implements ServerInputSubscription
    {
        protected final Subscription subscription;
        protected final FragmentHandler fragmentHandler;

        public ServerInputSubscriptionImpl(
                ServerOutput output,
                Subscription subscription,
                RemoteAddressList addressList,
                ServerMessageHandler messageHandler,
                ServerRequestHandler requestHandler)
        {
            this.subscription = subscription;
            this.fragmentHandler = new ServerReceiveHandler(output, addressList, messageHandler, requestHandler);
        }

        @Override
        public int poll()
        {
            return subscription.poll(fragmentHandler, Integer.MAX_VALUE);
        }
    }

}
