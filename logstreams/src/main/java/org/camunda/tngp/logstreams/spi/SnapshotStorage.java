package org.camunda.tngp.logstreams.spi;

public interface SnapshotStorage
{
    ReadableSnapshot getLastSnapshot(String name) throws Exception;

    SnapshotWriter createSnapshot(String name, long logPosition) throws Exception;
}
