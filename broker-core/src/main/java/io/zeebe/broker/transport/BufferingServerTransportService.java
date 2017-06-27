package io.zeebe.broker.transport;

import java.net.InetSocketAddress;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.Transports;
import io.zeebe.util.actor.ActorScheduler;

public class BufferingServerTransportService implements Service<BufferingServerTransport>
{
    protected final Injector<ActorScheduler> schedulerInjector = new Injector<>();
    protected final Injector<Dispatcher> receiveBufferInjector = new Injector<>();
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();

    protected final String readableName;
    protected final InetSocketAddress bindAddress;

    protected BufferingServerTransport serverTransport;

    public BufferingServerTransportService(String readableName, InetSocketAddress bindAddress)
    {
        this.readableName = readableName;
        this.bindAddress = bindAddress;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final ActorScheduler scheduler = schedulerInjector.getValue();
        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();
        final Dispatcher sendBuffer = sendBufferInjector.getValue();

        serverTransport = Transports.newServerTransport()
            .bindAddress(bindAddress)
            .sendBuffer(sendBuffer)
            .scheduler(scheduler)
            .buildBuffering(receiveBuffer);
        System.out.format("Bound %s to %s.\n", readableName, bindAddress);
    }

    @Override
    public void stop(ServiceStopContext serviceStopContext)
    {
        serviceStopContext.async(serverTransport.closeAsync());
    }

    @Override
    public BufferingServerTransport get()
    {
        return serverTransport;
    }

    public Injector<Dispatcher> getReceiveBufferInjector()
    {
        return receiveBufferInjector;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public Injector<ActorScheduler> getSchedulerInjector()
    {
        return schedulerInjector;
    }

}
