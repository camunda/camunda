package org.camunda.tngp.transport.protocol;

import org.camunda.tngp.dispatcher.Dispatcher;

public class AsyncProtocolContext
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
    protected DeferredMessagePool responsePool;

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

    public DeferredMessagePool getResponsePool()
    {
        return responsePool;
    }

    public void setResponsePool(DeferredMessagePool responsePool)
    {
        this.responsePool = responsePool;
    }
}
