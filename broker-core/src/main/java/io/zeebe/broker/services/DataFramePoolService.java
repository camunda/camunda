package io.zeebe.broker.services;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.singlemessage.DataFramePool;

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
