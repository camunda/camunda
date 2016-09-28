package org.camunda.tngp.transport.singlemessage;

import org.agrona.MutableDirectBuffer;

public interface OutgoingDataFrame extends AutoCloseable
{

    MutableDirectBuffer getBuffer();

    boolean isOpen();

    void commit();

    void abort();

    void close();
}
