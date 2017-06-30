package io.zeebe.dispatcher.impl.allocation;

import java.nio.ByteBuffer;

public class DirectBufferAllocator implements BufferAllocator<AllocationDescriptor>
{

    public AllocatedBuffer allocate(AllocationDescriptor configuration)
    {

        final int bufferCapacity = (int) configuration.getCapacity();

        return new ExternallyAllocatedBuffer(ByteBuffer.allocateDirect(bufferCapacity));
    }

}
