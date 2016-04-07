package org.camunda.tngp.transport.requestresponse.server;

import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.connectionIdOffset;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.headerLength;
import static org.camunda.tngp.transport.requestresponse.TransportRequestHeaderDescriptor.requestIdOffset;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.MessageHandler;

/**
 * {@link MessageHandler} implementation for data fragments which constitute requests.
 * Decodes the request headers, opens a deferred response and invokes the {@link AsyncRequestHandler}.
 *
 */
class RequestFragmentHandler implements MessageHandler
{
    protected final DeferredResponsePool responsePool;
    protected final AsyncRequestHandler asyncRequestHandler;

    public RequestFragmentHandler(AsyncWorkerContext context)
    {
        this.responsePool = context.getResponsePool();
        this.asyncRequestHandler = context.getRequestHandler();
    }

    @Override
    public void onMessage(int channelId, MutableDirectBuffer buffer, int index, int length)
    {
        final long connectionId = buffer.getLong(connectionIdOffset(index));
        final long requestId = buffer.getLong(requestIdOffset(index));

        final int requestOffset = index + headerLength();
        final int requestLength = length - headerLength();

        DeferredResponse response = responsePool.open(channelId, connectionId, requestId);

        if(response != null)
        {
            try
            {
                asyncRequestHandler.onRequest(buffer, requestOffset, requestLength, response);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                if(!response.isDeferred())
                {
                    responsePool.reclaim(response);
                }
            }

        }
        else
        {
            System.err.println("Dropping frame on channel "+channelId+", deferred response leak?");
        }
    }
}