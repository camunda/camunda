package io.zeebe.logstreams.log;

import org.agrona.DirectBuffer;
import io.zeebe.util.buffer.BufferWriter;

public interface LogStreamWriter
{

    void wrap(LogStream log);

    LogStreamWriter positionAsKey();

    LogStreamWriter key(long key);

    LogStreamWriter sourceEvent(DirectBuffer logStreamTopicName, int logStreamPartitionId, long position);

    LogStreamWriter producerId(int producerId);

    LogStreamWriter metadata(DirectBuffer buffer, int offset, int length);

    LogStreamWriter metadata(DirectBuffer buffer);

    LogStreamWriter metadataWriter(BufferWriter writer);

    LogStreamWriter value(DirectBuffer value, int valueOffset, int valueLength);

    LogStreamWriter value(DirectBuffer value);

    LogStreamWriter valueWriter(BufferWriter writer);

    void reset();

    /**
     * Attempts to write the event to the underlying stream.
     *
     * @return the event position or a negative value if fails to write the
     *         event
     */
    long tryWrite();

}
