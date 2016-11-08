package org.camunda.tngp.broker.transport.worker;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;

public class BrokerRequestWorkerContextService implements Service<AsyncRequestWorkerContext>
{
    protected final Injector<Dispatcher> asyncWorkBufferInjector = new Injector<>();
    protected final Injector<Dispatcher> requestBufferInjector = new Injector<>();
    protected final Injector<DeferredResponsePool> responsePoolInjector = new Injector<>();

    protected final AsyncRequestWorkerContext ctx;

    protected Subscription requestSubscription;

    public BrokerRequestWorkerContextService(AsyncRequestWorkerContext context)
    {
        this.ctx = context;
    }

    @Override
    public void start(ServiceStartContext serviceStartContext)
    {
        requestSubscription = requestBufferInjector.getValue().openSubscription(String.format("worker-%s", serviceStartContext.getName()));

        ctx.setRequestBufferSubscription(requestSubscription);
        ctx.setResponsePool(responsePoolInjector.getValue());
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        requestSubscription.close();
    }

    @Override
    public AsyncRequestWorkerContext get()
    {
        return ctx;
    }

    public Injector<Dispatcher> getRequestBufferInjector()
    {
        return requestBufferInjector;
    }

    public Injector<DeferredResponsePool> getResponsePoolInjector()
    {
        return responsePoolInjector;
    }

}
