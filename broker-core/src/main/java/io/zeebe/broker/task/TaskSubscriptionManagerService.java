package io.zeebe.broker.task;

import java.util.concurrent.CompletableFuture;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

public class TaskSubscriptionManagerService implements Service<TaskSubscriptionManager>
{
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    protected final Injector<ServerTransport> transportInjector = new Injector<>();

    protected ServiceStartContext serviceContext;

    protected TaskSubscriptionManager service;
    protected ActorReference actorRef;

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd((name, stream) -> service.addStream(stream, name))
        .onRemove((name, stream) -> service.removeStream(stream))
        .build();

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        service = new TaskSubscriptionManager(startContext);
        actorRef = actorScheduler.schedule(service);

        final ServerTransport clientApiTransport = transportInjector.getValue();
        final CompletableFuture<Void> transportRegistration = clientApiTransport.registerChannelListener(service);
        startContext.async(transportRegistration);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        actorRef.close();
    }

    @Override
    public TaskSubscriptionManager get()
    {
        return service;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

    public Injector<ServerTransport> getClientApiTransportInjector()
    {
        return transportInjector;
    }

}
