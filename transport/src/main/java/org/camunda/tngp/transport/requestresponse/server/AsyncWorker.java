package org.camunda.tngp.transport.requestresponse.server;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentHandler;

import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.MessageHandler;
import uk.co.real_logic.agrona.concurrent.ringbuffer.OneToOneRingBuffer;

/**
 * Base class for implementing asynchronous request/response server workers processing requests which block on i/o.
 *<p />
 * Setup:
 * <ul>
 * <li>A request buffer to which incoming requests are submitted</li>
 * <li>A response write buffer to which responses are written</li>
 * <li>An async work dispatcher to which asynchronous work is submitted and operates in pipeline mode</li>
 * </ul>
 *
 * Requirement: response must be deferred until asynchronous work completes.
 *  Completion is tracked by acting as the last consumer on the async work pipeline.
 *
 *<p/>
 *
 * Workflow:
 * <ul>
 * <li>requests are taken from the request buffer.</li>
 * <li>the request header is decoded and the corresponding {@link AsyncRequestHandler} is determined</li>
 * <li>the request is passed to the {@link AsyncRequestHandler} which processes it.</li>
 * <li>if the request requires a response, the response is allocated from a {@link DeferredResponsePool}.</li>
 * <li>this involves claiming the required space on the response send buffer</li>
 * <li>this needs to be done *before* the request submits asynchronous work</li>
 * <li>the response is then deferred until the async work completes</li>
 * <li>after that the response is committed</li>
 * </ul>
 */
public class AsyncWorker implements Agent
{
    protected final String name;

    protected final MessageHandler fragmentHandler;
    protected final OneToOneRingBuffer requestBuffer;
    protected final Dispatcher asyncWorkBuffer;
    protected final DeferredResponsePool responsePool;
    protected final int subscriberId;

    public AsyncWorker(String name, AsyncWorkerContext context)
    {
        this.name = name;
        this.fragmentHandler = new RequestFragmentHandler(context);
        this.asyncWorkBuffer = context.getAsyncWorkBuffer();
        this.requestBuffer = context.getRequestBuffer();
        this.responsePool = context.getResponsePool();
        this.subscriberId = asyncWorkBuffer.getSubscriberCount() -1;
    }

    public int doWork() throws Exception
    {
        int workCount = 0;

        final int pooledResponseCount = responsePool.getPooledCount();

        if(pooledResponseCount > 0)
        {
            // poll for as many incoming requests as pooled responses are available
            workCount += requestBuffer.read(fragmentHandler, pooledResponseCount);
        }

        // poll for completion on the work buffer to send out deferred responses
        while(asyncWorkBuffer.pollBlock(subscriberId, responsePool, responsePool.getCapacity(), false) > 0)
        {
            ++workCount;
        }

        return workCount;
    }

    @Override
    public String roleName()
    {
        return name;
    }

}
