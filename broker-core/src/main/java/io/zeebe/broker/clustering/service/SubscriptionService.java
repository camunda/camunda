package io.zeebe.broker.clustering.service;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

// TODO: still needed?
public class SubscriptionService implements Service<Subscription>
{
    private final Injector<Dispatcher> receiveBufferInjector = new Injector<>();

    private Subscription subscription;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();
        subscription = receiveBuffer.openSubscription(startContext.getName());
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(subscription.closeAsnyc());
    }

    @Override
    public Subscription get()
    {
        return subscription;
    }

    public Injector<Dispatcher> getReceiveBufferInjector()
    {
        return receiveBufferInjector;
    }

}
