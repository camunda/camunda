package org.camunda.tngp.transport.requestresponse.server;

import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.connectionIdOffset;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.headerLength;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.requestIdOffset;

import org.camunda.tngp.dispatcher.FragmentHandler;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.MessageHandler;

/**
 * {@link MessageHandler} implementation for data fragments which constitute requests.
 * Decodes the request headers, opens a deferred response and invokes the {@link AsyncRequestHandler}.
 *
 */
public class RequestFragmentHandler implements FragmentHandler
{
    protected final DeferredResponsePool responsePool;
    protected final AsyncRequestHandler asyncRequestHandler;

    public RequestFragmentHandler(AsyncRequestWorkerContext context)
    {
        this.responsePool = context.getResponsePool();
        this.asyncRequestHandler = context.getRequestHandler();
    }

    @Override
    public int onFragment(DirectBuffer buffer, int offset, int length, int channelId, boolean isMarkedFailed)
    {
        final long connectionId = buffer.getLong(connectionIdOffset(offset));
        final long requestId = buffer.getLong(requestIdOffset(offset));

        final int requestOffset = offset + headerLength();
        final int requestLength = length - headerLength();

        final DeferredResponse response = responsePool.open(channelId, connectionId, requestId);

        if (response != null)
        {
            try
            {
                asyncRequestHandler.onRequest(buffer, requestOffset, requestLength, response);
                return FragmentHandler.CONSUME_FRAGMENT_RESULT;
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                if (!response.isDeferred())
                {
                    responsePool.reclaim(response);
                }
            }

        }
        else
        {
            System.err.println("Dropping frame on channel " + channelId + ", deferred response leak?");
        }

        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
    }
}