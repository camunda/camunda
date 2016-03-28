package org.camunda.tngp.transport.requestresponse.server;

import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.util.BoundedArrayQueue;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

/**
 * Utility for deferring responses which wait on some async processing (usually io)
 * to complete before they are sent.
 */
public class DeferredResponsePool implements BlockHandler
{
    protected UnsafeBuffer flyweight = new UnsafeBuffer(0,0);

    protected BoundedArrayQueue<DeferredResponse> pooled;

    protected BoundedArrayQueue<DeferredResponse> deferred;

    protected final Dispatcher sendBuffer;

    protected int capacity;

    public DeferredResponsePool(Dispatcher sendBuffer, int capacity)
    {
        this.sendBuffer = sendBuffer;
        this.capacity = capacity;
        this.pooled = new BoundedArrayQueue<>(capacity);
        this.deferred = new BoundedArrayQueue<>(capacity);

        for(int i = 0; i < capacity; i++)
        {
            pooled.offer(new DeferredResponse(sendBuffer));
        }
    }

    public DeferredResponse open(int channelId, long connectionId, long requestId)
    {
        final DeferredResponse request = pooled.poll();

        if(request != null)
        {
            deferred.offer(request);
            request.open(channelId, connectionId, requestId);
        }

        return request;
    }

    @Override
    public void onBlockAvailable(
            final ByteBuffer buffer,
            final int blockOffset,
            final int blockLength,
            final int streamId,
            final long blockPosition)
    {
        while(deferred.size() > 0)
        {
            final DeferredResponse msg = deferred.peek();

            if(msg.asyncOperationId <= blockPosition)
            {
                deferred.remove();
                try
                {
                    int messageOffset = messageOffset(blockOffset);
                    int length = blockLength - HEADER_LENGTH;

                    flyweight.wrap(buffer, messageOffset, length);

                    msg.resolve(flyweight, messageOffset, length);
                }
                finally
                {
                    flyweight.wrap(0,0);
                    msg.reset();
                    pooled.offer(msg);
                }
            }
            else
            {
                break;
            }
        }
    }

    public int getCapacity()
    {
        return capacity;
    }

    public int getDeferredCount()
    {
        return deferred.size();
    }

    public int getPooledCount()
    {
        return pooled.size();
    }

}
