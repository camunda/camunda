package org.camunda.tngp.broker.transport.worker;

import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorker;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;

public class WorkerServiceNames
{
    public static ServiceName<DeferredResponsePool> workerResponsePoolServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("workers.%s.response-pool", bindingName), DeferredResponsePool.class);
    }

    public static ServiceName<AsyncRequestWorkerContext> workerContextServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("workers.%s.ctx", bindingName), AsyncRequestWorkerContext.class);
    }

    public static ServiceName<AsyncRequestWorker> workerServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("workers.%s", bindingName), AsyncRequestWorker.class);
    }

    public static ServiceName<DataFramePool> workerDataFramePoolServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("workers.%s.data-frame-pool", bindingName), DataFramePool.class);
    }


}
