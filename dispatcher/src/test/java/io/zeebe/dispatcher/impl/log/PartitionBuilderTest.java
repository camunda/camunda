/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_COUNT;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_META_DATA_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocators;
import org.junit.Before;
import org.junit.Test;

public class PartitionBuilderTest {

  PartitionBuilder partitionBuilder;

  @Before
  public void setup() {
    partitionBuilder = new PartitionBuilder();
  }

  @Test
  public void shouldSlicePartitions() {
    final int partitionSize = 1024;
    final int capacity =
        (PARTITION_COUNT * partitionSize) + (PARTITION_COUNT * PARTITION_META_DATA_LENGTH);
    final AllocatedBuffer buffer = BufferAllocators.allocateDirect(capacity);

    final LogBufferPartition[] partitions = partitionBuilder.slicePartitions(partitionSize, buffer);

    assertThat(partitions.length).isEqualTo(PARTITION_COUNT);

    for (LogBufferPartition logBufferPartition : partitions) {
      assertThat(logBufferPartition.getPartitionSize()).isEqualTo(partitionSize);
      assertThat(logBufferPartition.getDataBuffer().capacity()).isEqualTo(partitionSize);
    }
  }
}
