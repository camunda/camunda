package io.zeebe.util.allocation;

import java.nio.ByteBuffer;

public class AllocatedDirectBuffer extends AllocatedBuffer
{

    public AllocatedDirectBuffer(ByteBuffer buffer)
    {
        super(buffer);
    }

    @Override
    public void close()
    {
        AllocationUtil.freeDirectBuffer(rawBuffer);
    }

}
