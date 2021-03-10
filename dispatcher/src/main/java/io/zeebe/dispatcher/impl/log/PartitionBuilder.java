/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_COUNT;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_META_DATA_LENGTH;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.partitionDataSectionOffset;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.partitionMetadataSectionOffset;

import io.zeebe.util.allocation.AllocatedBuffer;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class PartitionBuilder {

  public LogBufferPartition[] slicePartitions(
      final int partitionSize, final AllocatedBuffer allocatedBuffer) {
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
