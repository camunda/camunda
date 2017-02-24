package org.camunda.tngp.broker.clustering.service;

import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class TransportConnectionPoolService implements Service<TransportConnectionPool>
{
    protected final Injector<Transport> transportInjector = new Injector<>();

    protected TransportConnectionPool connectionPool;

    @Override
    public void start(final ServiceStartContext serviceContext)
    {
        final Transport transport = transportInjector.getValue();
        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, 100, 2);
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
