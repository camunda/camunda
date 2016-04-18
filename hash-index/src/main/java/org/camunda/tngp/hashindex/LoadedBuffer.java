package org.camunda.tngp.hashindex;

import java.nio.ByteBuffer;

import org.camunda.tngp.hashindex.store.IndexStore;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LoadedBuffer
{
    protected final IndexStore indexStore;

    protected final boolean isDirect;

    protected final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

    protected long position;

    public LoadedBuffer(IndexStore indexStore, boolean isDirect)
    {
        this(indexStore, isDirect, 0, 0);
    }

    public LoadedBuffer(IndexStore indexStore, boolean isDirect, long initialOffset, int initialCapacity)
    {
        this.indexStore = indexStore;
        this.isDirect = isDirect;
        load(initialOffset, initialCapacity);
    }

    public void load(long position, int length)
    {
        this.position = position;

        ByteBuffer byteBuffer = buffer.byteBuffer();

        if(byteBuffer == null || byteBuffer.capacity() < length)
        {
            if(isDirect)
            {
                byteBuffer = ByteBuffer.allocateDirect(length);
            }
            else
            {
                byteBuffer = ByteBuffer.allocate(length);
            }
        }

        byteBuffer.position(0);
        byteBuffer.limit(length);

        indexStore.read(byteBuffer, position);

        buffer.wrap(byteBuffer, 0, length);
    }

    public void ensureLoaded(long position, int length)
    {
        if(this.position != position || this.buffer.capacity() != length)
        {
            load(position, length);
        }
    }

    public MutableDirectBuffer getBuffer()
    {
        return buffer;
    }

    public void write()
    {
        final ByteBuffer byteBuffer = buffer.byteBuffer();

        byteBuffer.position(0);
        byteBuffer.limit(buffer.capacity());

        indexStore.write(byteBuffer, position);
    }

    public long getPosition()
    {
        return position;
    }

}
