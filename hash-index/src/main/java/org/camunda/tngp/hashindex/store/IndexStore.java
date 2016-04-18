package org.camunda.tngp.hashindex.store;

import java.nio.ByteBuffer;

public interface IndexStore
{
    void read(ByteBuffer buffer, long position);

    void write(ByteBuffer buffer, long position);

    long allocate(int length);
}
