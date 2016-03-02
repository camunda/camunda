package net.long_running.dispatcher.impl.allocation;

import java.nio.ByteBuffer;

public class DirectBufferAllocator implements BufferAllocator<AllocationDescriptor>
{

    public AllocatedBuffer allocate(AllocationDescriptor configuration)
    {

        int bufferCapacity = (int) configuration.getCapacity();

        return new ExternallyAllocatedBuffer(ByteBuffer.allocateDirect(bufferCapacity));
    }

}
