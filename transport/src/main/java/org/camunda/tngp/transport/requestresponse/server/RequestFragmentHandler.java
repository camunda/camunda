package org.camunda.tngp.transport.requestresponse.server;

import static org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor.connectionIdOffset;
import static org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor.headerLength;
import static org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor.requestIdOffset;

import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
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
        final int requestResponseOffset = offset + TransportHeaderDescriptor.headerLength();
        final int requestResponseLength = length - TransportHeaderDescriptor.headerLength();

        final long connectionId = buffer.getLong(connectionIdOffset(requestResponseOffset));
        final long requestId = buffer.getLong(requestIdOffset(requestResponseOffset));

        final int requestOffset = requestResponseOffset + headerLength();
        final int requestLength = requestResponseLength - headerLength();

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