package org.camunda.tngp.broker.transport;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.impl.agent.Conductor;
import org.camunda.tngp.transport.impl.agent.Receiver;
import org.camunda.tngp.transport.impl.agent.Sender;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;

public class TransportService implements Service<Transport>
{
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    protected Transport transport;
    protected ActorReference transportConductorRef;
    protected ActorReference receiverRef;
    protected ActorReference senderRef;

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final TransportBuilder transportBuilder = Transports.createTransport(serviceContext.getName());

        transport = transportBuilder
                .sendBuffer(sendBufferInjector.getValue())
                .actorsExternallyManaged()
                .build();

        final Conductor transportConductor = transportBuilder.getTransportConductor();
        final Receiver receiver = transportBuilder.getReceiver();
        final Sender sender = transportBuilder.getSender();

        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
        receiverRef = actorScheduler.schedule(receiver);
        senderRef = actorScheduler.schedule(sender);
        transportConductorRef = actorScheduler.schedule(transportConductor);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        receiverRef.close();
        senderRef.close();
        transportConductorRef.close();
    }

    @Override
    public Transport get()
    {
        return transport;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }
}
