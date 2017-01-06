package org.camunda.tngp.hashindex.store;

import java.nio.ByteBuffer;

public interface IndexStore
{
    int read(ByteBuffer buffer, long position);

    void write(ByteBuffer buffer, long position);

    long allocate(int length);

    void clear();
}
