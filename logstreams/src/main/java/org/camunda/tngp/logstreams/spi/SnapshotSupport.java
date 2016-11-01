package org.camunda.tngp.logstreams.spi;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface to be implemented by resources which are used by
 * stream processors and support snapshots.
 */
public interface SnapshotSupport
{
    /**
     * write a snapshot to the provided output stream
     * @param outputStream the stream to write to
     */
    void writeSnapshot(OutputStream outputStream) throws Exception;

    /**
     * read a snapshot from the provided input stream.
     * @param inputStream the stream to read from
     */
    void recoverFromSnapshot(InputStream inputStream) throws Exception;
}
