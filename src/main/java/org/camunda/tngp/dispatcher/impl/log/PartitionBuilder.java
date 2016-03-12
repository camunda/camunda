package org.camunda.tngp.dispatcher.impl.log;

import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.*;

import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.impl.allocation.AllocatedBuffer;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

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
