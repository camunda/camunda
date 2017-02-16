package org.camunda.tngp.hashindex.buffer;

import java.nio.ByteBuffer;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.hashindex.store.IndexStore;

public class LoadedBuffer
{
    protected final IndexStore indexStore;
    protected final UnsafeBuffer buffer;

    protected long position = -1;

    public LoadedBuffer(IndexStore indexStore, int capacity)
    {
        this.indexStore = indexStore;
        this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(capacity));
    }

    public void load(long position)
    {
        final ByteBuffer byteBuffer = buffer.byteBuffer();

        byteBuffer.clear();
        indexStore.readFully(byteBuffer, position);

        this.position = position;
    }

    public void write()
    {
        final ByteBuffer byteBuffer = buffer.byteBuffer();

        byteBuffer.clear();

        indexStore.writeFully(byteBuffer, position);
    }

    public void unload()
    {
        buffer.setMemory(0, buffer.capacity(), (byte) 0);
        position = -1;
    }

    public MutableDirectBuffer getBuffer()
    {
        return buffer;
    }
}
