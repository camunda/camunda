package org.camunda.tngp.broker.transport.worker;

import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorker;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;

public class WorkerServiceNames
{
    public final static ServiceName<DeferredResponsePool> workerResponsePoolServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("workers.%s.response-pool", bindingName), DeferredResponsePool.class);
    }

    public final static ServiceName<AsyncRequestWorkerContext> workerContextServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("workers.%s.ctx", bindingName), AsyncRequestWorkerContext.class);
    }

    public final static ServiceName<AsyncRequestWorker> workerServiceName(String bindingName)
    {
        return ServiceName.newServiceName(String.format("workers.%s", bindingName), AsyncRequestWorker.class);
    }

}
