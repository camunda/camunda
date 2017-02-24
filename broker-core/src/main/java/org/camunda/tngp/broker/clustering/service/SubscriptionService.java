package org.camunda.tngp.broker.clustering.service;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

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
