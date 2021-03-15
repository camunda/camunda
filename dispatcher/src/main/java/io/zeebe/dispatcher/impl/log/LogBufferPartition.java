/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_CLEAN;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_NEEDS_CLEANING;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_STATUS_OFFSET;
import static io.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_TAIL_COUNTER_OFFSET;

import org.agrona.concurrent.UnsafeBuffer;

public class LogBufferPartition {

  /** The size of the partition */
  protected final int partitionSize;

  /** buffer containing the data section for the page */
  protected final UnsafeBuffer dataBuffer;

  /** buffer containing the metadata section for the page */
  protected final UnsafeBuffer metadataBuffer;

  /**
   * the offset of the partition's data buffer in the underlying buffer (see {@link
   * #underlyingBuffer}.
   */
  protected final int rawBufferOffset;

  public LogBufferPartition(
      final UnsafeBuffer dataBuffer, final UnsafeBuffer metadataBuffer, final int rawBufferOffset) {
    dataBuffer.verifyAlignment();
    metadataBuffer.verifyAlignment();
    this.dataBuffer = dataBuffer;
    this.metadataBuffer = metadataBuffer;
    partitionSize = dataBuffer.capacity();
    this.rawBufferOffset = rawBufferOffset;
    dataBuffer.setMemory(0, partitionSize, (byte) 0);
  }

  public void clean() {
    dataBuffer.setMemory(0, partitionSize, (byte) 0);
    metadataBuffer.putInt(PARTITION_TAIL_COUNTER_OFFSET, 0);
    setStatusOrdered(PARTITION_CLEAN);
  }

  public UnsafeBuffer getDataBuffer() {
    return dataBuffer;
  }

  public int getTailCounterVolatile() {
    return metadataBuffer.getIntVolatile(PARTITION_TAIL_COUNTER_OFFSET);
  }

  public int getAndAddTail(final int frameLength) {
    return metadataBuffer.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, frameLength);
  }

  public int getPartitionSize() {
    return partitionSize;
  }

  public void setStatusOrdered(final int status) {
    metadataBuffer.putIntOrdered(PARTITION_STATUS_OFFSET, status);
  }

  public int getStatusVolatile() {
    return metadataBuffer.getIntVolatile(PARTITION_STATUS_OFFSET);
  }

  public boolean needsCleaning() {
    return getStatusVolatile() == PARTITION_NEEDS_CLEANING;
  }

  public int getUnderlyingBufferOffset() {
    return rawBufferOffset;
  }
}
