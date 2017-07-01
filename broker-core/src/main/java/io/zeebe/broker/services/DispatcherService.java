package io.zeebe.broker.services;

import java.util.concurrent.CompletableFuture;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.DispatcherBuilder;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

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
