package io.zeebe.dispatcher.impl.allocation;

public class AllocationDescriptor
{

    long bufferCapacity;

    public AllocationDescriptor(int requiredCapacity)
    {
        bufferCapacity = requiredCapacity;
    }

    public long getCapacity()
    {
        return bufferCapacity;
    }

    public void getCapacity(long logCapacity)
    {
        this.bufferCapacity = logCapacity;
    }

}
