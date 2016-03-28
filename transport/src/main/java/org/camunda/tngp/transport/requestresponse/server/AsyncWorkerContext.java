package org.camunda.tngp.transport.requestresponse.server;

import org.camunda.tngp.dispatcher.Dispatcher;

public class AsyncWorkerContext
{
    /**
     * The buffer on which incoming requests become available
     */
    protected Dispatcher requestBuffer;

    /**
     * The buffer to which responses are written. (Send buffer for responses)
     */
    protected Dispatcher responseBuffer;

    /**
     * The buffer to which asynchronous work is submitted.
     * Responses are deferred / blocked until that work is complete.
     */
    protected Dispatcher asyncWorkBuffer;

    /**
     * The pool from which deferred responses are allocated
     */
    protected DeferredResponsePool responsePool;

    protected AsyncRequestHandler requestHandler;

    public Dispatcher getRequestBuffer()
    {
        return requestBuffer;
    }

    public void setRequestBuffer(Dispatcher requestBuffer)
    {
        this.requestBuffer = requestBuffer;
    }

    public Dispatcher getResponseBuffer()
    {
        return responseBuffer;
    }

    public void setResponseBuffer(Dispatcher responseBuffer)
    {
        this.responseBuffer = responseBuffer;
    }

    public Dispatcher getAsyncWorkBuffer()
    {
        return asyncWorkBuffer;
    }

    public void setAsyncWorkBuffer(Dispatcher asyncWorkBuffer)
    {
        this.asyncWorkBuffer = asyncWorkBuffer;
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
}
