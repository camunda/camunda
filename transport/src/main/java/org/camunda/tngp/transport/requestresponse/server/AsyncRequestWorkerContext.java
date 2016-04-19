package org.camunda.tngp.transport.requestresponse.server;

import org.camunda.tngp.dispatcher.impl.Subscription;

public class AsyncRequestWorkerContext
{
    /**
     * The buffer on which incoming requests become available
     */
    protected Subscription requestBufferSubscription;

    /**
     * The buffer to which asynchronous work is submitted.
     * Responses are deferred / blocked until that work is complete.
     */
    protected Subscription asyncWorkBufferSubscription;

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

    public Subscription getAsyncWorkBufferSubscription()
    {
        return asyncWorkBufferSubscription;
    }

    public void setAsyncWorkBufferSubscription(Subscription asyncWorkBufferSubscription)
    {
        this.asyncWorkBufferSubscription = asyncWorkBufferSubscription;
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
