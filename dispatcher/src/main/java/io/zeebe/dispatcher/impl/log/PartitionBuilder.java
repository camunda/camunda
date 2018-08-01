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
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.partitionDataSectionOffset;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.partitionMetadataSectionOffset;

import io.zeebe.util.allocation.AllocatedBuffer;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PartitionBuilder {

  public LogBufferPartition[] slicePartitions(int partitionSize, AllocatedBuffer allocatedBuffer) {
    final ByteBuffer buffer = allocatedBuffer.getRawBuffer();
    final LogBufferPartition[] partitions = new LogBufferPartition[PARTITION_COUNT];

    for (int i = 0; i < PARTITION_COUNT; i++) {
      final int dataSectionOffset = partitionDataSectionOffset(partitionSize, i);
      final int metaDataSectionOffset = partitionMetadataSectionOffset(partitionSize, i);

      final UnsafeBuffer dataSection = new UnsafeBuffer(buffer, dataSectionOffset, partitionSize);
      final UnsafeBuffer metadataSection =
          new UnsafeBuffer(buffer, metaDataSectionOffset, PARTITION_META_DATA_LENGTH);

      partitions[i] = new LogBufferPartition(dataSection, metadataSection, dataSectionOffset);
    }

    return partitions;
  }
}
