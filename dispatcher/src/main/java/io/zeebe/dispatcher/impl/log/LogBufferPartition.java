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
      UnsafeBuffer dataBuffer, UnsafeBuffer metadataBuffer, int rawBufferOffset) {
    dataBuffer.verifyAlignment();
    metadataBuffer.verifyAlignment();
    this.dataBuffer = dataBuffer;
    this.metadataBuffer = metadataBuffer;
    this.partitionSize = dataBuffer.capacity();
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

  public int getAndAddTail(int frameLength) {
    return metadataBuffer.getAndAddInt(PARTITION_TAIL_COUNTER_OFFSET, frameLength);
  }

  public int getPartitionSize() {
    return partitionSize;
  }

  public void setStatusOrdered(int status) {
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
