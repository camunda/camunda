package io.zeebe.broker.transport;

import java.net.InetSocketAddress;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerTransport;
import io.zeebe.transport.Transports;
import io.zeebe.util.actor.ActorScheduler;

public class ServerTransportService implements Service<ServerTransport>
{
    protected final Injector<ActorScheduler> schedulerInjector = new Injector<>();
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final Injector<ServerRequestHandler> requestHandlerInjector = new Injector<>();
    protected final Injector<ServerMessageHandler> messageHandlerInjector = new Injector<>();

    protected final String readableName;
    protected final InetSocketAddress bindAddress;

    protected ServerTransport serverTransport;

    public ServerTransportService(String readableName, InetSocketAddress bindAddress)
    {
        this.readableName = readableName;
        this.bindAddress = bindAddress;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final ActorScheduler scheduler = schedulerInjector.getValue();
        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final ServerRequestHandler requestHandler = requestHandlerInjector.getValue();
        final ServerMessageHandler messageHandler = messageHandlerInjector.getValue();

        serverTransport = Transports.newServerTransport()
            .bindAddress(bindAddress)
            .sendBuffer(sendBuffer)
            .scheduler(scheduler)
            .build(messageHandler, requestHandler);
        System.out.format("Bound %s to %s.\n", readableName, bindAddress);
    }

    @Override
    public void stop(ServiceStopContext serviceStopContext)
    {
        serviceStopContext.async(serverTransport.closeAsync());
    }

    @Override
    public ServerTransport get()
    {
        return serverTransport;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public Injector<ServerRequestHandler> getRequestHandlerInjector()
    {
        return requestHandlerInjector;
    }

    public Injector<ServerMessageHandler> getMessageHandlerInjector()
    {
        return messageHandlerInjector;
    }

    public Injector<ActorScheduler> getSchedulerInjector()
    {
        return schedulerInjector;
    }

}
