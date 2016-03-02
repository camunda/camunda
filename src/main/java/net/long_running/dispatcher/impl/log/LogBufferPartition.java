package net.long_running.dispatcher.impl.log;

import static net.long_running.dispatcher.impl.log.LogBufferDescriptor.*;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LogBufferPartition
{

    /**
     * The size of the partition
     */
    protected final int partitionSize;

    /**
     *  buffer containing the data section for the page
     */
    protected final UnsafeBuffer dataBuffer;

    /**
     * buffer containing the metadata section for the page
     */
    protected final UnsafeBuffer metadataBuffer;

    public LogBufferPartition(UnsafeBuffer dataBuffer, UnsafeBuffer metadataBuffer)
    {
        dataBuffer.verifyAlignment();
        metadataBuffer.verifyAlignment();
        this.dataBuffer = dataBuffer;
        this.metadataBuffer = metadataBuffer;
        this.partitionSize = dataBuffer.capacity();
    }

    public void clean()
    {
        dataBuffer.setMemory(0, partitionSize, (byte) 0);
        metadataBuffer.putInt(PARTITION_TAIL_COUNTER_OFFSET, 0);
        setStatusOrdered(PARTITION_CLEAN);
    }

    public UnsafeBuffer getDataBuffer()
    {
        return dataBuffer;
    }

    public int getTailCounterVolatile()
    {
        return metadataBuffer.getIntVolatile(PARTITION_TAIL_COUNTER_OFFSET);
    }

    public int getAndAddTail(int frameLength)
    {
        return metadataBuffer.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, frameLength);
    }

    public int getPartitionSize()
    {
        return partitionSize;
    }

    public void setStatusOrdered(int status)
    {
        metadataBuffer.putIntOrdered(PARTITION_STATUS_OFFSET, status);
    }

    public int getStatusVolatile()
    {
        return metadataBuffer.getIntVolatile(PARTITION_STATUS_OFFSET);
    }

    public boolean needsCleaning()
    {
        return getStatusVolatile() == PARTITION_NEEDS_CLEANING;
    }

}
