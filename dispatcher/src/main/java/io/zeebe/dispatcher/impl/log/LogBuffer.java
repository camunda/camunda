/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_ACTIVE_PARTITION_ID_OFFSET;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.LOG_INITIAL_PARTITION_ID_OFFSET;
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

  public LogBuffer(AllocatedBuffer allocatedBuffer, int partitionSize, int initialPartitionId) {
    this.partitionSize = partitionSize;
    rawBuffer = allocatedBuffer;

    partitions = new PartitionBuilder().slicePartitions(partitionSize, rawBuffer);

    metadataBuffer =
        new UnsafeBuffer(
            rawBuffer.getRawBuffer(), logMetadataOffset(partitionSize), LOG_META_DATA_LENGTH);

    metadataBuffer.putInt(LOG_INITIAL_PARTITION_ID_OFFSET, initialPartitionId);
    metadataBuffer.putIntVolatile(LOG_ACTIVE_PARTITION_ID_OFFSET, initialPartitionId);
  }

  public LogBufferPartition getPartition(int id) {
    return partitions[id % getPartitionCount()];
  }

  public int getActivePartitionIdVolatile() {
    return metadataBuffer.getIntVolatile(LOG_ACTIVE_PARTITION_ID_OFFSET);
  }

  public int getInitialPartitionId() {
    return metadataBuffer.getInt(LOG_INITIAL_PARTITION_ID_OFFSET);
  }

  public int getPartitionCount() {
    return partitions.length;
  }

  public int getDataFrameMaxLength() {
    return metadataBuffer.getInt(LOG_MAX_FRAME_LENGTH_OFFSET);
  }

  public void onActiveParitionFilled(int activePartitionId) {
    final int nextPartitionId = 1 + activePartitionId;
    final int nextNextPartitionId = 1 + nextPartitionId;
    final LogBufferPartition nextNextPartition =
        partitions[(nextNextPartitionId) % getPartitionCount()];

    nextNextPartition.setStatusOrdered(PARTITION_NEEDS_CLEANING);
    metadataBuffer.putIntOrdered(LOG_ACTIVE_PARTITION_ID_OFFSET, nextPartitionId);
  }

  public int cleanPartitions() {
    int workCount = 0;

    for (int i = 0; i < LogBufferDescriptor.PARTITION_COUNT; i++) {
      final LogBufferPartition partition = partitions[i];

      if (partition.getStatusVolatile() == PARTITION_NEEDS_CLEANING) {
        partition.clean();
        ++workCount;
      }
    }

    return workCount;
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
