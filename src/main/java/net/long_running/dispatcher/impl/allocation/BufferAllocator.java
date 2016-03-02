package net.long_running.dispatcher.impl.allocation;

public interface BufferAllocator<T extends AllocationDescriptor>
{

    AllocatedBuffer allocate(T configuration);

}
