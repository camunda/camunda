package org.camunda.tngp.transport.requestresponse.server;

import org.camunda.tngp.dispatcher.Subscription;

public class AsyncRequestWorkerContext
{
    /**
     * The buffer on which incoming requests become available
     */
    protected Subscription requestBufferSubscription;

    /**
     * The pool from which deferred responses are allocated
     */
    protected DeferredResponsePool responsePool;

    protected AsyncRequestHandler requestHandler;

    protected WorkerTask[] workerTasks;

    public Subscription getRequestBufferSubscription()
    {
        return requestBufferSubscription;
    }

    public void setRequestBufferSubscription(Subscription requestBufferSubscription)
    {
        this.requestBufferSubscription = requestBufferSubscription;
    }

    public DeferredResponsePool getResponsePool()
    {
        return responsePool;
    }

    public void setResponsePool(DeferredResponsePool responsePool)
    {
        this.responsePool = responsePool;
    }

    public AsyncRequestHandler getRequestHandler()
    {
        return requestHandler;
    }

    public void setRequestHandler(AsyncRequestHandler requestHandler)
    {
        this.requestHandler = requestHandler;
    }

    public void setWorkerTasks(WorkerTask[] workerTasks)
    {
        this.workerTasks = workerTasks;
    }

    public WorkerTask[] getWorkerTasks()
    {
        return workerTasks;
    }
}
