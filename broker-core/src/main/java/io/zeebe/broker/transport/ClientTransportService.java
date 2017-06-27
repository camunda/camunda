package io.zeebe.broker.transport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.Transports;
import io.zeebe.util.actor.ActorScheduler;

public class ClientTransportService implements Service<ClientTransport>
{
    protected final Injector<ActorScheduler> schedulerInjector = new Injector<>();
    protected final Injector<Dispatcher> receiveBufferInjector = new Injector<>();
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final int requestPoolSize;

    protected ClientTransport transport;

    public ClientTransportService(int requestPoolSize)
    {
        this.requestPoolSize = requestPoolSize;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {

        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();
        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final ActorScheduler scheduler = schedulerInjector.getValue();

        transport = Transports.newClientTransport()
            .messageReceiveBuffer(receiveBuffer)
            .sendBuffer(sendBuffer)
            .requestPoolSize(requestPoolSize)
            .scheduler(scheduler)
            .build();
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(transport.closeAsync());
    }

    @Override
    public ClientTransport get()
    {
        return transport;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public Injector<Dispatcher> getReceiveBufferInjector()
    {
        return receiveBufferInjector;
    }

    public Injector<ActorScheduler> getSchedulerInjector()
    {
        return schedulerInjector;
    }

}
