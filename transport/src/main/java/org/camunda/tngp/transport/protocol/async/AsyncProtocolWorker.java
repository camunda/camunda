package org.camunda.tngp.transport.protocol.async;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.transport.protocol.MessageHeaderDecoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;
import uk.co.real_logic.agrona.concurrent.Agent;

/**
 * Base class for implementing asynchronous protocols working in the following way:
 *<p />
 * Setup:
 * <ul>
 * <li>A request buffer to which incoming requests are submitted</li>
 * <li>A response write buffer to which responses are written</li>
 * <li>An async work dispatcher to which asynchronous work is submitted and operates in pipeline mode</li>
 * </ul>
 *
 * Requirement: response must be deferred until asynchronous work completes. Completion is tracked by acting as the last consumer on the
 * async work pipeline.
 *
 *<p/>
 *
 * Workflow:
 * <ul>
 * <li>requests are taken from the request buffer</li>
 * <li>the request header is decoded and the corresponding {@link AsyncRequestHandler} is determined</li>
 * <li>the request is passed to the {@link AsyncRequestHandler} which processes it.</li>
 * <li>if the request requires a response, the response is allocated from a {@link DeferredMessagePool}.</li>
 * <li>this involves claiming the required space on the response send buffer</li>
 * <li>this needs to be done *before* the request submits asynchronous work</li>
 * <li>the response is then deferred until the async work completes</li>
 * <li>after that the response is committed</li>
 * </ul>
 */
public abstract class AsyncProtocolWorker implements Agent
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    protected final Int2ObjectHashMap<AsyncRequestHandler> requestHandlers = new Int2ObjectHashMap<>();

    protected final Dispatcher requestBuffer;
    protected final Dispatcher asyncWorkBuffer;
    protected final DeferredMessagePool responsePool;

    protected final FragmentHandler inflowHandler = new FragmentHandler()
    {
        public void onFragment(DirectBuffer buffer, int offset, int length, int channelId)
        {
            headerDecoder.wrap(buffer, offset);

            final int blockLength = headerDecoder.blockLength();
            final int templateId = headerDecoder.templateId();
            final int version = headerDecoder.version();
            final long requestId = headerDecoder.requestId();

            final AsyncRequestHandler msgHandler = requestHandlers.get(templateId);

            if(msgHandler != null)
            {
                msgHandler.onRequest(
                    requestId,
                    channelId,
                    buffer,
                    offset + MessageHeaderDecoder.ENCODED_LENGTH,
                    length - MessageHeaderDecoder.ENCODED_LENGTH,
                    blockLength,
                    version);
            }
            else
            {
                System.err.println("Unrecognized message templateId="+templateId);
            }
        }
    };

    public AsyncProtocolWorker(AsyncProtocolContext context)
    {
        asyncWorkBuffer = context.getAsyncWorkBuffer();
        requestBuffer = context.getRequestBuffer();
        responsePool = context.getResponsePool();
    }

    protected void registerHandler(AsyncRequestHandler handler)
    {
        requestHandlers.put(handler.getTemplateId(), handler);
    }

    public int doWork() throws Exception
    {
        final int subscriberId = asyncWorkBuffer.getSubscriberCount() -1;

        int workCount = 0;

        workCount += requestBuffer.poll(inflowHandler, 10);

        while(asyncWorkBuffer.pollBlock(subscriberId, responsePool, 1, false) > 0)
        {
            ++workCount;
        }

        return workCount;
    }

}
