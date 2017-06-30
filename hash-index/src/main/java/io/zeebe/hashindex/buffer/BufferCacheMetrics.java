package io.zeebe.hashindex.buffer;

public interface BufferCacheMetrics
{
    long getCacheMisses();

    long getCacheLookups();

    long getCacheHits();
}