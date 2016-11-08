package org.camunda.tngp.broker.services;

import java.util.function.Consumer;

import org.agrona.collections.Long2ObjectCache;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class Long2ObjectChacheService<V> implements Service<Long2ObjectCache<V>>
{
    protected final int numSets;
    protected final int setSize;
    protected final Consumer<V> evictionConsumer;

    protected Long2ObjectCache<V> theCache;

    public Long2ObjectChacheService(
            final int numSets,
            final int setSize,
            final Consumer<V> evictionConsumer)
    {
        this.numSets = numSets;
        this.setSize = setSize;
        this.evictionConsumer = evictionConsumer;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        theCache = new Long2ObjectCache<>(numSets, setSize, evictionConsumer);
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        // nothing to do
    }

    @Override
    public Long2ObjectCache<V> get()
    {
        return theCache;
    }

}
