package org.camunda.tngp.logstreams.spi;

import java.io.OutputStream;

/**
 * Writer to create a snapshot.
 */
public interface SnapshotWriter
{

    /**
     * Returns the output stream of the snapshot which should be used to write
     * the snapshot data. Finish the write operation with {@link #commit()} or
     * {@link #abort()}.
     *
     * @return the snapshot output stream
     */
    OutputStream getOutputStream();

    /**
     * Completes the snapshot by closing the output stream and writing the
     * checksum.
     *
     * @throws Exception
     *             if fails to write the checksum
     */
    void commit() throws Exception;

    /**
     * Refuse the snapshot by closing the output stream and deleting the
     * snapshot data.
     */
    void abort();

}
