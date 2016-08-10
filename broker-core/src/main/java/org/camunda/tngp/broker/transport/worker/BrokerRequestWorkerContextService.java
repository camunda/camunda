package org.camunda.tngp.broker.transport.worker;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;

public class BrokerRequestWorkerContextService implements Service<AsyncRequestWorkerContext>
{
    protected final Injector<Dispatcher> asyncWorkBufferInjector = new Injector<>();
    protected final Injector<Dispatcher> requestBufferInjector = new Injector<>();
    protected final Injector<DeferredResponsePool> responsePoolInjector = new Injector<>();

    protected final AsyncRequestWorkerContext ctx;

    protected Subscription asyncWorkSubscription;
    protected Subscription requestSubscription;

    public BrokerRequestWorkerContextService(AsyncRequestWorkerContext context)
    {
        this.ctx = context;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        requestSubscription = requestBufferInjector.getValue().openSubscription();

        ctx.setRequestBufferSubscription(requestSubscription);
        ctx.setResponsePool(responsePoolInjector.getValue());
    }

    @Override
    public void stop()
    {
        asyncWorkSubscription.close();
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

    public Injector<Dispatcher> getAsyncWorkBufferInjector()
    {
        return asyncWorkBufferInjector;
    }

}
