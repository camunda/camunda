package io.zeebe.broker.transport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.Transport;
import io.zeebe.transport.TransportBuilder;
import io.zeebe.transport.Transports;
import io.zeebe.transport.impl.agent.Conductor;
import io.zeebe.transport.impl.agent.Receiver;
import io.zeebe.transport.impl.agent.Sender;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

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
