package org.camunda.tngp.broker.clustering.channel;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.ReceiveBufferChannelHandler;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;


public class ClientChannelManagerService implements Service<ChannelManager>
{
    public static final ServiceName<ChannelManager> CLIENT_CHANNEL_POOL = ServiceName.newServiceName("client.channel.pool", ChannelManager.class);

    protected final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<>();
    protected final Injector<Transport> transportInjector = new Injector<>();
    protected final Injector<Dispatcher> receiveBufferInjector = new Injector<>();

    protected final int capacity;
    protected ChannelManager clientChannelPool;


    public ClientChannelManagerService(final int capacity)
    {
        this.capacity = capacity;
    }

    @Override
    public void start(final ServiceStartContext serviceContext)
    {

        final Transport transport = transportInjector.getValue();
        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();
        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();

        clientChannelPool = transport.createClientChannelPool()
                .requestResponseProtocol(connectionPool)
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, new ReceiveBufferChannelHandler(receiveBuffer))
                .build();
    }

    @Override
    public void stop(final ServiceStopContext stopContext)
    {
    }

    @Override
    public ChannelManager get()
    {
        return clientChannelPool;
    }

    public Injector<Transport> getTransportInjector()
    {
        return transportInjector;
    }

    public Injector<TransportConnectionPool> getTransportConnectionPoolInjector()
    {
        return transportConnectionPoolInjector;
    }

    public Injector<Dispatcher> getReceiveBufferInjector()
    {
        return receiveBufferInjector;
    }

}
