package io.zeebe.broker.clustering.service;

import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.Transport;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;

public class TransportConnectionPoolService implements Service<TransportConnectionPool>
{
    protected final Injector<Transport> transportInjector = new Injector<>();

    protected TransportConnectionPool connectionPool;

    @Override
    public void start(final ServiceStartContext serviceContext)
    {
        final Transport transport = transportInjector.getValue();
        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, 100, 128);
    }

    @Override
    public void stop(final ServiceStopContext stopContext)
    {
        stopContext.run(() -> connectionPool.close());
    }

    @Override
    public TransportConnectionPool get()
    {
        return connectionPool;
    }

    public Injector<Transport> getTransportInjector()
    {
        return transportInjector;
    }

}
