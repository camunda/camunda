package io.zeebe.logstreams.spi;

import java.io.InputStream;

import io.zeebe.logstreams.snapshot.InvalidSnapshotException;

/**
 * Represents a snapshot of the log.
 */
public interface ReadableSnapshot
{
    /**
     * The log position at which the snapshot was taken.
     */
    long getPosition();

    /**
     * Input stream to read the snapshot data.
     *
     * @return the snapshot data as input stream
     */
    InputStream getData();

    /**
     * Consumers of this API must call this method after having read the input
     * stream. The method validates that the bytes read are valid and closes any
     * underlying resources.
     *
     * @throws InvalidSnapshotException
     *             if not valid
     */
    void validateAndClose() throws InvalidSnapshotException;

    /**
     * Deletes the snapshot and related data.
     */
    void delete();

    /**
     * Reads the snapshot data and recover the given snapshot object. At the end,
     * it validates that the bytes read are valid and closes any underlying resources.
     *
     * @param snapshotSupport
     *            the snapshot object
     * @throws Exception
     *             if fails to recover the snapshot object
     * @throws InvalidSnapshotException
     *             if the snapshot is not valid
     */
    default void recoverFromSnapshot(SnapshotSupport snapshotSupport) throws Exception
    {
        snapshotSupport.reset();
        snapshotSupport.recoverFromSnapshot(getData());
        validateAndClose();
    }

}
