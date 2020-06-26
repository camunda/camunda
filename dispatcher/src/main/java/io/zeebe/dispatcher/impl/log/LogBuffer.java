/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_ACTIVE_PARTITION_ID_OFFSET;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_MAX_FRAME_LENGTH_OFFSET;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_META_DATA_LENGTH;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_NEEDS_CLEANING;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.logMetadataOffset;

import io.zeebe.dispatcher.Loggers;
import io.zeebe.util.allocation.AllocatedBuffer;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class LogBuffer {
  public static final Logger LOG = Loggers.DISPATCHER_LOGGER;

  protected final AllocatedBuffer rawBuffer;

  protected final LogBufferPartition[] partitions;

  protected final UnsafeBuffer metadataBuffer;

  protected final int partitionSize;

  public LogBuffer(final AllocatedBuffer allocatedBuffer, final int partitionSize) {
    this.partitionSize = partitionSize;
    rawBuffer = allocatedBuffer;

    partitions = new PartitionBuilder().slicePartitions(partitionSize, rawBuffer);

    metadataBuffer =
        new UnsafeBuffer(
            rawBuffer.getRawBuffer(), logMetadataOffset(partitionSize), LOG_META_DATA_LENGTH);

    metadataBuffer.putIntVolatile(LOG_ACTIVE_PARTITION_ID_OFFSET, 0);
  }

  public LogBufferPartition getPartition(final int id) {
    return partitions[id % getPartitionCount()];
  }

  public int getActivePartitionIdVolatile() {
    return metadataBuffer.getIntVolatile(LOG_ACTIVE_PARTITION_ID_OFFSET);
  }

  public int getPartitionCount() {
    return partitions.length;
  }

  public int getDataFrameMaxLength() {
    return metadataBuffer.getInt(LOG_MAX_FRAME_LENGTH_OFFSET);
  }

  public void onActivePartitionFilled(final int activePartitionId) {
    final int nextPartitionId = 1 + activePartitionId;
    final int nextNextPartitionId = 1 + nextPartitionId;
    final LogBufferPartition nextNextPartition =
        partitions[(nextNextPartitionId) % getPartitionCount()];

    nextNextPartition.setStatusOrdered(PARTITION_NEEDS_CLEANING);
    metadataBuffer.putIntOrdered(LOG_ACTIVE_PARTITION_ID_OFFSET, nextPartitionId);

    LOG.trace(
        "Partition {} is filled, mark partition {} as active",
        (activePartitionId % getPartitionCount()),
        (nextPartitionId % getPartitionCount()));
  }

  public void cleanPartitions() {
    for (int i = 0; i < LogBufferDescriptor.PARTITION_COUNT; i++) {
      final LogBufferPartition partition = partitions[i];

      if (partition.getStatusVolatile() == PARTITION_NEEDS_CLEANING) {
        LOG.trace("Clean partition {}", i);

        partition.clean();
      }
    }
  }

  public void close() {
    rawBuffer.close();
  }

  public boolean isClosed() {
    return rawBuffer.isClosed();
  }

  public int getPartitionSize() {
    return partitionSize;
  }

  public ByteBuffer createRawBufferView() {
    return rawBuffer.getRawBuffer().duplicate();
  }
}
