package io.zeebe.util.allocation;

import java.nio.ByteBuffer;

public class HeapBufferAllocator implements BufferAllocator
{

    @Override
    public AllocatedBuffer allocate(int capacity)
    {
        return new ExternallyAllocatedBuffer(ByteBuffer.allocate(capacity));
    }

}
