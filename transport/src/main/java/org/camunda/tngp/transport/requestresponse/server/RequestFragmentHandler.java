package org.camunda.tngp.transport.requestresponse.server;

import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.connectionIdOffset;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.headerLength;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.requestIdOffset;

import org.camunda.tngp.dispatcher.FragmentHandler;

import uk.co.real_logic.agrona.DirectBuffer;

/**
 * {@link FragmentHandler} implementation for data fragments which constitute requests.
 * Decodes the request headers, opens a deferred response and invokes the {@link AsyncRequestHandler}.
 *
 */
class RequestFragmentHandler implements FragmentHandler
{
    protected final DeferredResponsePool responsePool;
    protected final AsyncRequestHandler asyncRequestHandler;

    public RequestFragmentHandler(AsyncWorkerContext context)
    {
        this.responsePool = context.getResponsePool();
        this.asyncRequestHandler = context.getRequestHandler();
    }

    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, int channelId)
    {
        final long connectionId = buffer.getLong(connectionIdOffset(offset));
        final long requestId = buffer.getLong(requestIdOffset(offset));

        final int requestOffset = offset + headerLength();
        final int requestLength = length - headerLength();

        DeferredResponse response = responsePool.open(channelId, connectionId, requestId);

        if(response != null)
        {
            asyncRequestHandler.onRequest(buffer, requestOffset, requestLength, response);
        }
        else
        {
            System.err.println("Dropping frame on channel "+channelId+", deferred response leak?");
        }
    }
}