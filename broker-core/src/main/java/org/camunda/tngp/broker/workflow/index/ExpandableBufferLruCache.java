package org.camunda.tngp.broker.workflow.index;

import java.nio.ByteBuffer;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.LongLruCache;
import org.agrona.concurrent.UnsafeBuffer;

public class ExpandableBufferLruCache
{
    private final DirectBuffer readBuffer = new UnsafeBuffer(0, 0);

    private final MutableDirectBuffer[] recycledBuffers;

    private final LongFunction<DirectBuffer> lookup;

    private LongLruCache<MutableDirectBuffer> cache;

    public ExpandableBufferLruCache(int cacheCapacity, int initialBufferCapacity, LongFunction<DirectBuffer> lookup)
    {
        this.lookup = lookup;

        cache = new LongLruCache<>(cacheCapacity, this::getRecycledBuffer, this::recycle);

        recycledBuffers = new MutableDirectBuffer[cacheCapacity + 1];
        for (int i = 0; i < recycledBuffers.length; i++)
        {
            recycledBuffers[i] = new ExpandableDirectByteBuffer(initialBufferCapacity);
        }
    }

    private MutableDirectBuffer getRecycledBuffer(long key)
    {
        MutableDirectBuffer recycledBuffer = null;

        for (int i = 0; i < recycledBuffers.length; i++)
        {
            recycledBuffer = recycledBuffers[i];

            if (recycledBuffer != null)
            {
                recycledBuffers[i] = null;

                final ByteBuffer byteBuffer = recycledBuffer.byteBuffer();
                byteBuffer.clear();

                final DirectBuffer buffer = lookup.apply(key);
                buffer.getBytes(0, byteBuffer, buffer.capacity());
                // use the limit to indicate the buffer length
                byteBuffer.limit(buffer.capacity());

                break;
            }
        }

        return recycledBuffer;
    }

    private void recycle(MutableDirectBuffer bufferToRecycle)
    {
        bufferToRecycle.setMemory(0, bufferToRecycle.capacity(), (byte) 0);

        for (int i = 0; i < recycledBuffers.length; i++)
        {
            if (recycledBuffers[i] == null)
            {
                recycledBuffers[i] = bufferToRecycle;
                return;
            }
        }
    }

    public DirectBuffer lookupBuffer(long key)
    {
        DirectBuffer result = null;

        final MutableDirectBuffer buffer = cache.lookup(key);
        if (buffer != null)
        {
            final ByteBuffer byteBuffer = buffer.byteBuffer();
            // wrap the buffer to the original size
            readBuffer.wrap(byteBuffer, 0, byteBuffer.limit());

            result = readBuffer;
        }

        return result;
    }

    public void clear()
    {
        cache.close();
        cache = new LongLruCache<>(cache.capacity(), this::getRecycledBuffer, this::recycle);
    }

}
