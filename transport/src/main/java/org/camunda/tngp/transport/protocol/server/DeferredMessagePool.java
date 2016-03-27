package org.camunda.tngp.transport.protocol.server;

import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.BlockHandler;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.util.BoundedArrayQueue;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.*;

/**
 * Utility for deferring messages which wait on some asynchronous work (usually I/O) to complete.
 */
public class DeferredMessagePool implements BlockHandler
{
    protected UnsafeBuffer flyweight = new UnsafeBuffer(0,0);

    protected BoundedArrayQueue<DeferredMessage> pooled;

    protected BoundedArrayQueue<DeferredMessage> deferred;

    protected final Dispatcher sendBuffer;

    public DeferredMessagePool(Dispatcher sendBuffer, int capacity)
    {
        this.sendBuffer = sendBuffer;
        this.pooled = new BoundedArrayQueue<>(capacity);
        this.deferred = new BoundedArrayQueue<>(capacity);

        for(int i = 0; i < capacity; i++)
        {
            pooled.offer(new DeferredMessage(sendBuffer));
        }
    }

    public DeferredMessage takeNext()
    {
        final DeferredMessage request = pooled.take();

        if(request != null)
        {
            deferred.offer(request);
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
            final DeferredMessage msg = deferred.peek();

            if(msg.completionPosition <= blockPosition)
            {
                deferred.take();
                pooled.offer(msg);

                int messageOffset = messageOffset(blockOffset);
                int length = blockLength - HEADER_LENGTH;

                flyweight.wrap(buffer, messageOffset, length);

                msg.resolve(flyweight, messageOffset, length);

                flyweight.wrap(0,0);
            }
            else
            {
                break;
            }

        }
    }

}
