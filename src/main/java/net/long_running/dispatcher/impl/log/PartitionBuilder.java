package net.long_running.dispatcher.impl.log;

import static net.long_running.dispatcher.impl.log.LogBufferDescriptor.*;

import java.nio.ByteBuffer;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class PartitionBuilder
{

    public LogBufferPartition[] slicePartitions(int partitionSize, ByteBuffer buffer)
    {
        LogBufferPartition[] partitions = new LogBufferPartition[PARTITION_COUNT];

        for (int i = 0; i < PARTITION_COUNT; i++)
        {
            final int dataSectionOffset = partitionDataSectionOffset(partitionSize, i);
            final int metaDataSectionOffset = partitionMetadataSectionOffset(partitionSize, i);

            final UnsafeBuffer dataSection = new UnsafeBuffer(buffer, dataSectionOffset, partitionSize);
            final UnsafeBuffer metadataSection = new UnsafeBuffer(buffer, metaDataSectionOffset, PARTITION_META_DATA_LENGTH);

            partitions[i] = new LogBufferPartition(dataSectionOffset, dataSection, metadataSection);
        }

        return partitions;
    }

}
