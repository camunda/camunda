package net.long_running.dispatcher.impl.log;

import static net.long_running.dispatcher.impl.log.LogBufferDescriptor.*;
import static org.assertj.core.api.Assertions.*;

import java.nio.ByteBuffer;

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
        int partitionSize = 1024;
        ByteBuffer buffer = ByteBuffer.allocate((PARTITION_COUNT * partitionSize) + (PARTITION_COUNT * PARTITION_META_DATA_LENGTH));

        LogBufferPartition[] partitions = partitionBuilder.slicePartitions(partitionSize, buffer);

        assertThat(partitions.length).isEqualTo(PARTITION_COUNT);

        for (LogBufferPartition logBufferPartition : partitions)
        {
            assertThat(logBufferPartition.getPartitionSize()).isEqualTo(partitionSize);
            assertThat(logBufferPartition.getDataBuffer().capacity()).isEqualTo(partitionSize);
        }

    }
}
