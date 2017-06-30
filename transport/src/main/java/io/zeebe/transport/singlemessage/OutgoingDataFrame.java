package io.zeebe.transport.singlemessage;

import org.agrona.MutableDirectBuffer;
import io.zeebe.util.buffer.BufferWriter;

public interface OutgoingDataFrame extends AutoCloseable
{

    MutableDirectBuffer getBuffer();

    void write(BufferWriter writer);

    boolean isOpen();

    void commit();

    void abort();

    void close();
}
