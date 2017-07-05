package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_ACTIVE_PARTITION_ID_OFFSET;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_INITIAL_PARTITION_ID_OFFSET;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_MAX_FRAME_LENGTH_OFFSET;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_META_DATA_LENGTH;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_NEEDS_CLEANING;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.logMetadataOffset;

import java.io.IOException;

import io.zeebe.dispatcher.Loggers;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.dispatcher.impl.allocation.AllocatedBuffer;
import org.slf4j.Logger;

public class LogBuffer
{
    public static final Logger LOG = Loggers.DISPATCHER_LOGGER;

    protected final AllocatedBuffer rawBuffer;

    protected final LogBufferPartition[] partitions;

    protected final UnsafeBuffer metadataBuffer;

    protected final int partitionSize;

    public LogBuffer(AllocatedBuffer allocatedBuffer, int partitionSize, int initialPartitionId)
    {
        this.partitionSize = partitionSize;
        rawBuffer = allocatedBuffer;

        partitions = new PartitionBuilder().slicePartitions(partitionSize, rawBuffer);

        metadataBuffer = new UnsafeBuffer(rawBuffer.getRawBuffer(), logMetadataOffset(partitionSize), LOG_META_DATA_LENGTH);

        metadataBuffer.putInt(LOG_INITIAL_PARTITION_ID_OFFSET, initialPartitionId);
        metadataBuffer.putIntVolatile(LOG_ACTIVE_PARTITION_ID_OFFSET, initialPartitionId);
    }

    public LogBufferPartition getPartition(int id)
    {
        return partitions[id % getPartitionCount()];
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
        final LogBufferPartition nextNextPartition = partitions[(nextNextPartitionId) % getPartitionCount()];

        nextNextPartition.setStatusOrdered(PARTITION_NEEDS_CLEANING);
        metadataBuffer.putIntOrdered(LOG_ACTIVE_PARTITION_ID_OFFSET, nextPartitionId);
    }

    public int cleanPartitions()
    {
        int workCount = 0;
        for (LogBufferPartition partition : partitions)
        {
            if (partition.getStatusVolatile() == PARTITION_NEEDS_CLEANING)
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
            LOG.error("Failed to close buffer", e);
        }
    }

    public int getPartitionSize()
    {
        return partitionSize;
    }

}
