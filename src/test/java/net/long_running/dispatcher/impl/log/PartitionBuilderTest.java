package net.long_running.dispatcher.impl.log;

import static net.long_running.dispatcher.impl.log.LogBufferDescriptor.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.Before;
import org.junit.Test;

import net.long_running.dispatcher.impl.allocation.AllocatedBuffer;
import net.long_running.dispatcher.impl.allocation.AllocationDescriptor;
import net.long_running.dispatcher.impl.allocation.DirectBufferAllocator;

public class PartitionBuilderTest
{

    PartitionBuilder partitionBuilder;

    @Before
    public void setup()
    {
        partitionBuilder = new PartitionBuilder();
    }

    @Test
    public void shouldSlicePartitions()
    {
        int partitionSize = 1024;
        AllocatedBuffer buffer = new DirectBufferAllocator()
                .allocate(new AllocationDescriptor((PARTITION_COUNT * partitionSize) + (PARTITION_COUNT * PARTITION_META_DATA_LENGTH)));

        LogBufferPartition[] partitions = partitionBuilder.slicePartitions(partitionSize, buffer);

        assertThat(partitions.length).isEqualTo(PARTITION_COUNT);

        for (LogBufferPartition logBufferPartition : partitions)
        {
            assertThat(logBufferPartition.getPartitionSize()).isEqualTo(partitionSize);
            assertThat(logBufferPartition.getDataBuffer().capacity()).isEqualTo(partitionSize);
        }

    }
}
