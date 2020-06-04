/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.impl.log;

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_MESSAGE;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.framedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static org.agrona.BitUtil.align;
import static org.agrona.UnsafeAccess.UNSAFE;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.ClaimedFragmentBatch;
import io.zeebe.dispatcher.Loggers;
import io.zeebe.util.TriConsumer;
import java.util.function.BiConsumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class LogBufferAppender {
  public static final int RESULT_PADDING_AT_END_OF_PARTITION = -2;
  public static final int RESULT_END_OF_PARTITION = -1;

  private static final Logger LOG = Loggers.DISPATCHER_LOGGER;

  public int claim(
      final LogBufferPartition partition,
      final int activePartitionId,
      final ClaimedFragment claim,
      final int length,
      final int streamId,
      final Runnable onComplete,
      final BiConsumer<Long, TriConsumer<ZeebeEntry, Long, Integer>> addHandler) {
    final int partitionSize = partition.getPartitionSize();
    final int framedMessageLength = framedLength(length);
    final int alignedFrameLength = alignedLength(framedMessageLength);

    // move the tail of the partition
    final int frameOffset = partition.getAndAddTail(alignedFrameLength);

    int newTail = frameOffset + alignedFrameLength;

    if (newTail <= (partitionSize - HEADER_LENGTH)) {
      final UnsafeBuffer buffer = partition.getDataBuffer();

      // write negative length field
      buffer.putIntOrdered(lengthOffset(frameOffset), -framedMessageLength);
      UNSAFE.storeFence();
      buffer.putShort(typeOffset(frameOffset), TYPE_MESSAGE);
      buffer.putInt(streamIdOffset(frameOffset), streamId);

      claim.wrap(buffer, frameOffset, framedMessageLength, onComplete, addHandler);
      // Do not commit the message
    } else {
      newTail = onEndOfPartition(partition, frameOffset, activePartitionId);
    }

    return newTail;
  }

  public int claim(
      final LogBufferPartition partition,
      final int activePartitionId,
      final ClaimedFragmentBatch batch,
      final int fragmentCount,
      final int batchLength,
      final Runnable onComplete,
      final BiConsumer<Long, TriConsumer<ZeebeEntry, Long, Integer>> addHandler) {
    final int partitionSize = partition.getPartitionSize();
    // reserve enough space for frame alignment because each batch fragment must start on an aligned
    // position
    final int framedMessageLength =
        batchLength + fragmentCount * (HEADER_LENGTH + FRAME_ALIGNMENT) + FRAME_ALIGNMENT;
    final int alignedFrameLength = align(framedMessageLength, FRAME_ALIGNMENT);

    // move the tail of the partition
    final int frameOffset = partition.getAndAddTail(alignedFrameLength);

    int newTail = frameOffset + alignedFrameLength;

    if (newTail <= (partitionSize - HEADER_LENGTH)) {
      final UnsafeBuffer buffer = partition.getDataBuffer();
      // all fragment data are written using the claimed batch
      batch.wrap(
          buffer, activePartitionId, frameOffset, alignedFrameLength, onComplete, addHandler);

    } else {
      newTail = onEndOfPartition(partition, frameOffset, activePartitionId);
    }

    return newTail;
  }

  protected int onEndOfPartition(
      final LogBufferPartition partition, final int partitionOffset, final int activePartitionId) {
    int newTail = RESULT_END_OF_PARTITION;

    final int padLength = partition.getPartitionSize() - partitionOffset;

    if (padLength >= HEADER_LENGTH) {
      LOG.trace(
          "The claimed size doesn't fit into the partition {}, fill the rest with padding",
          activePartitionId);

      // this message tripped the end of the partition, fill buffer with padding
      final UnsafeBuffer buffer = partition.getDataBuffer();
      buffer.putIntOrdered(lengthOffset(partitionOffset), -padLength);
      UNSAFE.storeFence();
      buffer.putShort(typeOffset(partitionOffset), TYPE_PADDING);
      buffer.putIntOrdered(lengthOffset(partitionOffset), padLength);

      newTail = RESULT_PADDING_AT_END_OF_PARTITION;

    } else {
      LOG.trace("The claimed size doesn't fit into the partition {}", activePartitionId);
    }

    return newTail;
  }
}
