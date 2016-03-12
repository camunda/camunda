package org.camunda.tngp.dispatcher.impl.log;

import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.*;

import java.io.IOException;

import org.camunda.tngp.dispatcher.impl.allocation.AllocatedBuffer;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class LogBuffer
{

    protected final AllocatedBuffer rawBuffer;

    protected final LogBufferPartition[] partitions;

    protected final UnsafeBuffer metadataBuffer;

    protected final int partitionSize;

    public LogBuffer(AllocatedBuffer allocatedBuffer, int partitionSize)
    {
        this.partitionSize = partitionSize;
        rawBuffer = allocatedBuffer;


        partitions = new PartitionBuilder().slicePartitions(partitionSize, rawBuffer);

        metadataBuffer = new UnsafeBuffer(rawBuffer.getRawBuffer(), logMetadataOffset(partitionSize), LOG_META_DATA_LENGTH);

    }

    public LogBufferPartition getPartition(int i)
    {
        return partitions[i];
    }

    public int getActivePartitionIdVolatile()
    {
        return metadataBuffer.getIntVolatile(LOG_ACTIVE_PARTITION_ID_OFFSET);
    }

    public int getInitialPartitionId()
    {
        return metadataBuffer.getInt(LOG_INITIAL_PARTITION_ID_OFFSET);
    }

    public int getPartitionCount()
    {
        return partitions.length;
    }

    public int getDataFrameMaxLength()
    {
        return metadataBuffer.getInt(LOG_MAX_FRAME_LENGTH_OFFSET);
    }

    public void onActiveParitionFilled(int activePartitionId)
    {
        final int nextPartitionId = 1 + activePartitionId;
        final int nextNextPartitionId = 1 + nextPartitionId;
        final LogBufferPartition nextNextPartition = partitions[nextNextPartitionId % 3];

        nextNextPartition.setStatusOrdered(PARTITION_NEEDS_CLEANING);
        metadataBuffer.putIntOrdered(LOG_ACTIVE_PARTITION_ID_OFFSET, nextPartitionId);
    }

    public int cleanPartitions()
    {
        int workCount = 0;
        for (LogBufferPartition partition : partitions)
        {
            if(partition.getStatusVolatile() == PARTITION_NEEDS_CLEANING)
            {
                partition.clean();
                ++workCount;
            }
        }
        return workCount;
    }

    public void close()
    {
        try
        {
            rawBuffer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public int getPartitionSize()
    {
        return partitionSize;
    }

}
