package org.camunda.tngp.dispatcher.impl.allocation;

public interface BufferAllocator<T extends AllocationDescriptor>
{

    AllocatedBuffer allocate(T configuration);

}
