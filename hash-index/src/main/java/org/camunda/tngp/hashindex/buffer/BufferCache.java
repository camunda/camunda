package org.camunda.tngp.hashindex.buffer;

import org.agrona.collections.LongLruCache;
import org.camunda.tngp.hashindex.store.IndexStore;

public class BufferCache implements BufferCacheMetrics
{
    private LongLruCache<LoadedBuffer> cache;

    private final LoadedBuffer[] recycledBuffers;

    private long cacheMisses = 0;
    private long cacheLookups = 0;

    private boolean shouldWriteOnEvict = true;

    public BufferCache(IndexStore indexStore, int cacheCapacity, int bufferCapacity)
    {
        cache = new LongLruCache<LoadedBuffer>(cacheCapacity, this::getRecycledBuffer, this::recycle);
        recycledBuffers = new LoadedBuffer[cacheCapacity + 1];

        for (int i = 0; i < recycledBuffers.length; i++)
        {
            recycledBuffers[i] = new LoadedBuffer(indexStore, bufferCapacity);
        }
    }

    public LoadedBuffer getBuffer(long position)
    {
        ++cacheLookups;
        return cache.lookup(position);
    }

    private LoadedBuffer getRecycledBuffer(long position)
    {
        LoadedBuffer buffer = null;

        for (int i = 0; i < recycledBuffers.length; i++)
        {
            buffer = recycledBuffers[i];

            if (buffer != null)
            {
                recycledBuffers[i] = null;
                buffer.load(position);
                break;
            }
        }

        ++cacheMisses;

        return buffer;
    }

    public void recycle(LoadedBuffer bufferToRecycle)
    {
        if (shouldWriteOnEvict)
        {
            bufferToRecycle.write();
        }

        bufferToRecycle.unload();

        for (int i = 0; i < recycledBuffers.length; i++)
        {
            if (recycledBuffers[i] == null)
            {
                recycledBuffers[i] = bufferToRecycle;
            }
        }
    }

    public void flush()
    {
        cache.close();
        cache = new LongLruCache<LoadedBuffer>(cache.capacity(), this::getRecycledBuffer, this::recycle);
        cacheMisses = 0;
    }

    public void clear()
    {
        shouldWriteOnEvict = false;
        try
        {
            flush();
        }
        finally
        {
            shouldWriteOnEvict = true;
        }
    }

    @Override
    public long getCacheMisses()
    {
        return cacheMisses;
    }

    @Override
    public long getCacheLookups()
    {
        return cacheLookups;
    }

    @Override
    public long getCacheHits()
    {
        return cacheLookups - cacheMisses;
    }

}
