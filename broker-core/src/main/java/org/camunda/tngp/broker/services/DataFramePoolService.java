package org.camunda.tngp.broker.services;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class DataFramePoolService implements Service<DataFramePool>
{
    protected Injector<Dispatcher> sendBufferInector = new Injector<>();
    protected final int capacity;

    protected DataFramePool dataFramePool;

    public DataFramePoolService(int capacity)
    {
        this.capacity = capacity;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        dataFramePool = DataFramePool.newBoundedPool(capacity, sendBufferInector.getValue());

    }

    @Override
    public void stop(ServiceStopContext serviceStopContext)
    {
    }

    @Override
    public DataFramePool get()
    {
        return dataFramePool;
    }

    public Injector<Dispatcher> getSendBufferInector()
    {
        return sendBufferInector;
    }

}
