package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_COUNT;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_META_DATA_LENGTH;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.partitionDataSectionOffset;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.partitionMetadataSectionOffset;

import java.nio.ByteBuffer;

import io.zeebe.dispatcher.impl.allocation.AllocatedBuffer;

import org.agrona.concurrent.UnsafeBuffer;

public class PartitionBuilder
{

    public LogBufferPartition[] slicePartitions(int partitionSize, AllocatedBuffer allocatedBuffer)
    {
        final ByteBuffer buffer = allocatedBuffer.getRawBuffer();
        final LogBufferPartition[] partitions = new LogBufferPartition[PARTITION_COUNT];

        for (int i = 0; i < PARTITION_COUNT; i++)
        {
            final int dataSectionOffset = partitionDataSectionOffset(partitionSize, i);
            final int metaDataSectionOffset = partitionMetadataSectionOffset(partitionSize, i);

            final UnsafeBuffer dataSection = new UnsafeBuffer(buffer, dataSectionOffset, partitionSize);
            final UnsafeBuffer metadataSection = new UnsafeBuffer(buffer, metaDataSectionOffset, PARTITION_META_DATA_LENGTH);

            partitions[i] = new LogBufferPartition(dataSection, metadataSection, allocatedBuffer, dataSectionOffset);
        }

        return partitions;
    }

}
