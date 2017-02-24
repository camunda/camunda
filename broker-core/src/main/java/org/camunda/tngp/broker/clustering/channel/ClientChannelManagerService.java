package org.camunda.tngp.broker.clustering.channel;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.ReceiveBufferChannelHandler;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;


public class ClientChannelManagerService implements Service<ClientChannelManager>
{
    public static final ServiceName<ClientChannelManager> CLIENT_CHANNEL_MANAGER = ServiceName.newServiceName("client.channel.manager", ClientChannelManager.class);

    protected final Injector<TransportConnectionPool> transportConnectionPoolInjector = new Injector<TransportConnectionPool>();
    protected final Injector<Transport> transportInjector = new Injector<Transport>();
    protected final Injector<Dispatcher> receiveBufferInjector = new Injector<Dispatcher>();

    protected final int capacity;
    protected ClientChannelManager clientChannelManager;


    public ClientChannelManagerService(final int capacity)
    {
        this.capacity = capacity;
    }

    @Override
    public void start(final ServiceStartContext serviceContext)
    {

        final TransportConnectionPool connectionPool = transportConnectionPoolInjector.getValue();
        final Transport transport = transportInjector.getValue();
        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();

        clientChannelManager = new ClientChannelManager(capacity, transport, (tr, addr) ->
        {
            return tr.createClientChannel(addr)
                    .requestResponseProtocol(connectionPool)
                    .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, new ReceiveBufferChannelHandler(receiveBuffer));
        });
    }

    @Override
    public void stop(final ServiceStopContext stopContext)
    {
    }

    @Override
    public ClientChannelManager get()
    {
        return clientChannelManager;
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
