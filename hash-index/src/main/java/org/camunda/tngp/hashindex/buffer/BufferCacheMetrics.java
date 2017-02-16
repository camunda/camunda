package org.camunda.tngp.hashindex.buffer;

public interface BufferCacheMetrics
{
    long getCacheMisses();

    long getCacheLookups();

    long getCacheHits();
}