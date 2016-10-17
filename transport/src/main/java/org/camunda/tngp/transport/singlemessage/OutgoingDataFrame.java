package org.camunda.tngp.transport.singlemessage;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.util.buffer.BufferWriter;

public interface OutgoingDataFrame extends AutoCloseable
{

    MutableDirectBuffer getBuffer();

    void write(BufferWriter writer);

    boolean isOpen();

    void commit();

    void abort();

    void close();
}
