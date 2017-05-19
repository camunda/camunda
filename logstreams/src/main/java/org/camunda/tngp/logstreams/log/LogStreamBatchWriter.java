package org.camunda.tngp.logstreams.log;

import org.agrona.DirectBuffer;
import org.camunda.tngp.util.buffer.BufferWriter;

/**
 * Write log entries to the log stream write buffer as batch. This ensures that
 * the log entries are written atomically.
 *
 * <p>
 * Note that the log entry data is buffered until {@link #tryWrite()} is called.
 */
public interface LogStreamBatchWriter
{
    /**
     * Builder to add a log entry to the batch.
     */
    interface LogEntryBuilder
    {
        /**
         * Use the log entry position as key.
         */
        LogEntryBuilder positionAsKey();

        /**
         * Set the log entry key.
         */
        LogEntryBuilder key(long key);

        /**
         * Set the log entry metadata.
         */
        LogEntryBuilder metadata(DirectBuffer buffer, int offset, int length);

        /**
         * Set the log entry metadata.
         */
        LogEntryBuilder metadata(DirectBuffer buffer);

        /**
         * Set the log entry metadata.
         */
        LogEntryBuilder metadataWriter(BufferWriter writer);

        /**
         * Set the log entry value.
         */
        LogEntryBuilder value(DirectBuffer value, int valueOffset, int valueLength);

        /**
         * Set the log entry value.
         */
        LogEntryBuilder value(DirectBuffer value);

        /**
         * Set the log entry value.
         */
        LogEntryBuilder valueWriter(BufferWriter writer);

        /**
         * Add the log entry to the batch.
         */
        LogStreamBatchWriter done();
    }

    /**
     * Initialize the write for the given log stream.
     */
    void wrap(LogStream log);

    /**
     * Set the source event for all log entries.
     */
    LogStreamBatchWriter sourceEvent(DirectBuffer logStreamTopicName, int logStreamPartitionId, long position);

    /**
     * Set the producer id for all log entries.
     */
    LogStreamBatchWriter producerId(int producerId);

    /**
     * Returns the builder to add a new log entry to the batch.
     */
    LogEntryBuilder event();

    /**
     * Attempts to write the batch to the underlying buffer.
     *
     * @return the position of the last written log entry, or a negative value
     *         if fails to claim the batch
     */
    long tryWrite();

    /**
     * Discard all non-written batch data.
     */
    void reset();
}
