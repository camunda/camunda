package io.zeebe.dispatcher.impl.allocation;

public interface BufferAllocator<T extends AllocationDescriptor>
{

    AllocatedBuffer allocate(T configuration);

}
