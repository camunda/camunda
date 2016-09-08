package org.camunda.tngp.dispatcher.impl.log;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.dispatcher.impl.log.LogBufferDescriptor.*;

import org.camunda.tngp.dispatcher.impl.allocation.AllocatedBuffer;
import org.camunda.tngp.dispatcher.impl.allocation.AllocationDescriptor;
import org.camunda.tngp.dispatcher.impl.allocation.DirectBufferAllocator;
import org.junit.Before;
import org.junit.Test;

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
        final int partitionSize = 1024;
        final AllocatedBuffer buffer = new DirectBufferAllocator().allocate(new AllocationDescriptor(
                (PARTITION_COUNT * partitionSize) + (PARTITION_COUNT * PARTITION_META_DATA_LENGTH)));

        final LogBufferPartition[] partitions = partitionBuilder.slicePartitions(partitionSize, buffer);

        assertThat(partitions.length).isEqualTo(PARTITION_COUNT);

        for (LogBufferPartition logBufferPartition : partitions)
        {
            assertThat(logBufferPartition.getPartitionSize()).isEqualTo(partitionSize);
            assertThat(logBufferPartition.getDataBuffer().capacity()).isEqualTo(partitionSize);
        }

    }
}
