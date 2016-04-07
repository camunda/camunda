package org.camunda.tngp.dispatcher.impl.log;

import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.*;

import org.camunda.tngp.dispatcher.impl.allocation.AllocatedBuffer;

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

    /**
     * the offset of the partition's data buffer in the underlying buffer (see {@link #underlyingBuffer}.
     */
    protected final int rawBufferOffset;

    /**
     * the raw buffer in which this partition is allocated into.
     * {@link #getUnderlyingBufferOffset()} is the offset of the data buffer in this buffer
     */
    protected final AllocatedBuffer underlyingBuffer;

    public LogBufferPartition(
            UnsafeBuffer dataBuffer,
            UnsafeBuffer metadataBuffer,
            AllocatedBuffer underlyingBuffer,
            int rawBufferOffset)
    {
        dataBuffer.verifyAlignment();
        metadataBuffer.verifyAlignment();
        this.dataBuffer = dataBuffer;
        this.metadataBuffer = metadataBuffer;
        this.partitionSize = dataBuffer.capacity();
        this.underlyingBuffer = underlyingBuffer;
        this.rawBufferOffset = rawBufferOffset;
        dataBuffer.setMemory(0, partitionSize, (byte) 0);
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

    public int getUnderlyingBufferOffset()
    {
        return rawBufferOffset;
    }

    public AllocatedBuffer getUnderlyingBuffer()
    {
        return underlyingBuffer;
    }
}
