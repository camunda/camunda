package io.zeebe.util.allocation;

import java.io.File;

public class BufferAllocators
{
    private static final DirectBufferAllocator DIRECT_BUFFER_ALLOCATOR = new DirectBufferAllocator();

    public static AllocatedBuffer allocateDirect(int capacity)
    {
        return DIRECT_BUFFER_ALLOCATOR.allocate(capacity);
    }

    public static AllocatedBuffer allocateMappedFile(int capacity, File file)
    {
        return new MappedFileAllocator(file).allocate(capacity);
    }

}
