package org.camunda.tngp.hashindex.store;

import java.nio.ByteBuffer;

public interface IndexStore extends AutoCloseable
{
    void readFully(ByteBuffer buffer, long position);

    int read(ByteBuffer buffer, long position);

    void writeFully(ByteBuffer buffer, long position);

    long allocate(int length);

    void clear();

    void flush();

    void close();
}
