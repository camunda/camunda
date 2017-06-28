package org.camunda.tngp.broker.services;

import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.DispatcherBuilder;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;

public class DispatcherService implements Service<Dispatcher>
{
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    protected final Injector<Counters> countersInjector = new Injector<>();

    protected DispatcherBuilder dispatcherBuilder;
    protected Dispatcher dispatcher;
    protected ActorReference conductorRef;

    public DispatcherService(int bufferSize)
    {
        this(Dispatchers.create(null).bufferSize(bufferSize));
    }

    public DispatcherService(DispatcherBuilder builder)
    {
        this.dispatcherBuilder = builder;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        final Counters counters = countersInjector.getValue();

        dispatcher = dispatcherBuilder
                .name(ctx.getName())
                .conductorExternallyManaged()
                .countersManager(counters.getCountersManager())
                .countersBuffer(counters.getCountersBuffer())
                .build();

        conductorRef = actorSchedulerInjector.getValue().schedule(dispatcher.getConductor());
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        final CompletableFuture<Void> closeFuture = dispatcher.closeAsync().thenAccept((v) ->
        {
            conductorRef.close();
        });

        ctx.async(closeFuture);
    }

    @Override
    public Dispatcher get()
    {
        return dispatcher;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<Counters> getCountersManagerInjector()
    {
        return countersInjector;
    }
}
