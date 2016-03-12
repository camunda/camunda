package org.camunda.tngp.dispatcher.impl.allocation;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class AllocatedBuffer implements Closeable
{

    protected final ByteBuffer rawBuffer;

    public AllocatedBuffer(ByteBuffer buffer)
    {
        this.rawBuffer = buffer;
    }

    public ByteBuffer getRawBuffer()
    {
        return rawBuffer;
    }

    /**
     * de-allocates the buffer along with all resources associated with it
     */
    @Override
    public abstract void close() throws IOException;

}
