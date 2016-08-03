package org.camunda.tngp.transport.requestresponse.server;

import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.messageOffset;

import java.nio.ByteBuffer;
import java.util.Queue;

import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.util.BoundedArrayQueue;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

/**
 * Utility for deferring responses which wait on some async processing (usually io)
 * to complete before they are sent.
 */
public class DeferredResponsePool implements BlockHandler
{
    protected UnsafeBuffer flyweight = new UnsafeBuffer(0, 0);

    protected BoundedArrayQueue<DeferredResponse> pooled;

    protected Queue<DeferredResponse> deferred;

    protected final Dispatcher sendBuffer;

    protected int capacity;

    public DeferredResponsePool(Dispatcher sendBuffer, int capacity)
    {
        this.sendBuffer = sendBuffer;
        this.capacity = capacity;
        this.pooled = new BoundedArrayQueue<>(capacity);
        this.deferred = new BoundedArrayQueue<>(capacity);

        for (int i = 0; i < capacity; i++)
        {
            pooled.offer(new DeferredResponse(sendBuffer, new DeferredResponseControl()
                {
                    @Override
                    public void defer(DeferredResponse r)
                    {
                        deferred.offer(r);
                    }
                }));
        }
    }

    public DeferredResponse open(int channelId, long connectionId, long requestId)
    {
        final DeferredResponse response = pooled.poll();

        if (response != null)
        {
            response.open(channelId, connectionId, requestId);
        }

        return response;
    }

    @Override
    public void onBlockAvailable(
            final ByteBuffer buffer,
            final int blockOffset,
            final int blockLength,
            final int streamId,
            final long blockPosition)
    {
        while (deferred.size() > 0)
        {
            final DeferredResponse msg = deferred.peek();

            if (msg.asyncOperationId <= blockPosition)
            {
                deferred.remove();
                try
                {
                    final int messageOffset = messageOffset(blockOffset);
                    final int length = blockLength - HEADER_LENGTH;

                    flyweight.wrap(buffer, messageOffset, length);

                    msg.resolve(flyweight, messageOffset, length, blockPosition);
                }
                finally
                {
                    flyweight.wrap(0, 0);
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

    public void reclaim(DeferredResponse response)
    {
        response.reset();
        pooled.offer(response);
    }

    public interface DeferredResponseControl
    {
        void defer(DeferredResponse r);
    }

}
