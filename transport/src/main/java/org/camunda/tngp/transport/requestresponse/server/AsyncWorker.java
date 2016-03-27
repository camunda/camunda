package org.camunda.tngp.transport.requestresponse.server;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentHandler;
import uk.co.real_logic.agrona.concurrent.Agent;

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
public abstract class AsyncWorker implements Agent
{
    protected final Dispatcher requestBuffer;
    protected final Dispatcher asyncWorkBuffer;
    protected final DeferredResponsePool responsePool;
    protected final FragmentHandler requestHandler;
    final int subscriberId;

    public AsyncWorker(AsyncWorkerContext context, FragmentHandler inflowHandler)
    {
        this.requestHandler = inflowHandler;
        asyncWorkBuffer = context.getAsyncWorkBuffer();
        requestBuffer = context.getRequestBuffer();
        responsePool = context.getResponsePool();
        subscriberId = asyncWorkBuffer.getSubscriberCount() -1;
    }

    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += requestBuffer.poll(requestHandler, responsePool.getPooledCount());

        while(asyncWorkBuffer.pollBlock(subscriberId, responsePool, 1, false) > 0)
        {
            ++workCount;
        }

        return workCount;
    }

}
