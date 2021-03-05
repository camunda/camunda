/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.dispatcher;

import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.FRAME_ALIGNMENT;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_MESSAGE;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.enableFlagBatchBegin;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.enableFlagBatchEnd;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.framedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static org.agrona.UnsafeAccess.UNSAFE;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A claimed batch of fragments in the buffer. Use {@link #nextFragment(int, int)} to add a new
 * fragment to the batch. Write the fragment message using {@link #getBuffer()} and {@link
 * #getFragmentOffset()} to get the buffer offset of this fragment. Complete the whole batch
 * operation by calling either {@link #commit()} or {@link #abort()}.
 *
 * <p><b>The claimed batch is reusable but not thread-safe.</b>
 */
public class ClaimedFragmentBatch {
  private static final String ERROR_MESSAGE =
      "The given fragment length is greater than the remaining capacity. offset: %d, length: %d, capacity: %d";

  private static final int FIRST_FRAGMENT_OFFSET = 0;

  private final UnsafeBuffer buffer;

  private int partitionId;
  private int partitionOffset;

  private int currentOffset;
  private int nextOffset;

  private Runnable onCompleteHandler;

  public ClaimedFragmentBatch() {
    buffer = new UnsafeBuffer(0, 0);
  }

  public void wrap(
      final UnsafeBuffer underlyingBuffer,
      final int partitionId,
      final int fragmentOffset,
      final int fragmentLength,
      final Runnable onCompleteHandler) {
    buffer.wrap(underlyingBuffer, fragmentOffset, fragmentLength);

    this.partitionId = partitionId;
    partitionOffset = fragmentOffset;

    currentOffset = 0;
    nextOffset = 0;

    this.onCompleteHandler = onCompleteHandler;
  }

  /** @return the claimed batch buffer to write in. */
  public MutableDirectBuffer getBuffer() {
    return buffer;
  }

  /** @return the buffer offset of the last batch fragment */
  public int getFragmentOffset() {
    return currentOffset + HEADER_LENGTH;
  }

  /**
   * Add a new fragment to the batch.
   *
   * @param length the length of the fragment
   * @param streamId the stream id of the fragment
   * @return the position of the fragment
   * @throws IllegalArgumentException if the given length is greater than the remaining capacity. In
   *     this case, you should try with smaller length, or abort the whole batch.
   */
  @SuppressWarnings("restriction")
  public long nextFragment(final int length, final int streamId) {
    currentOffset = nextOffset;

    final int framedLength = framedLength(length);
    nextOffset += alignedLength(framedLength);

    // ensure that there is enough capacity for padding message, or less than frame alignment which
    // omits the padding message
    final int remainingCapacity = buffer.capacity() - nextOffset;
    if (remainingCapacity < 0
        || (FRAME_ALIGNMENT <= remainingCapacity && remainingCapacity < HEADER_LENGTH)) {
      throw new IllegalArgumentException(
          String.format(ERROR_MESSAGE, currentOffset, length, buffer.capacity()));
    }

    // set negative length => uncommitted fragment
    buffer.putIntOrdered(lengthOffset(currentOffset), -framedLength);
    UNSAFE.storeFence();
    buffer.putShort(typeOffset(currentOffset), TYPE_MESSAGE);
    buffer.putInt(streamIdOffset(currentOffset), streamId);

    return position(partitionId, partitionOffset + nextOffset);
  }

  /** Commit all fragments of the batch so that it can be read by subscriptions. */
  public void commit() {
    final int firstFragmentFramedLength = -buffer.getInt(lengthOffset(FIRST_FRAGMENT_OFFSET));

    // do not set batch flags if only one fragment in the batch
    if (currentOffset > 0) {
      // set batch begin flag
      final byte firstFragmentFlags = buffer.getByte(flagsOffset(FIRST_FRAGMENT_OFFSET));
      buffer.putByte(flagsOffset(FIRST_FRAGMENT_OFFSET), enableFlagBatchBegin(firstFragmentFlags));

      // set positive length => commit fragment
      int fragmentOffset = DataFrameDescriptor.alignedLength(firstFragmentFramedLength);
      while (fragmentOffset < nextOffset) {
        final int fragmentFramedLength = -buffer.getInt(lengthOffset(fragmentOffset));
        buffer.putInt(lengthOffset(fragmentOffset), fragmentFramedLength);

        fragmentOffset += DataFrameDescriptor.alignedLength(fragmentFramedLength);
      }

      // set batch end flag
      final byte lastFragmentFlags = buffer.getByte(flagsOffset(currentOffset));
      buffer.putByte(flagsOffset(currentOffset), enableFlagBatchEnd(lastFragmentFlags));
    }

    fillRemainingBatchSize();

    // commit the first fragment at the end so that the batch can be read at
    // once
    buffer.putIntOrdered(lengthOffset(FIRST_FRAGMENT_OFFSET), firstFragmentFramedLength);
    onCompleteHandler.run();

    reset();
  }

  /**
   * Commit all fragments of the batch and mark them as failed. They will be ignored by
   * subscriptions.
   */
  public void abort() {
    // discard all fragments by set the type to padding
    int fragmentOffset = 0;
    while (fragmentOffset < nextOffset) {
      final int fragmentLength = -buffer.getInt(lengthOffset(fragmentOffset));
      buffer.putInt(typeOffset(fragmentOffset), TYPE_PADDING);
      buffer.putIntOrdered(lengthOffset(fragmentOffset), fragmentLength);

      fragmentOffset += DataFrameDescriptor.alignedLength(fragmentLength);
    }

    fillRemainingBatchSize();
    onCompleteHandler.run();

    reset();
  }

  private void fillRemainingBatchSize() {
    // since the claimed batch size can be longer than the written fragment
    // size, we need to fill the rest with a padding fragment
    final int remainingLength = buffer.capacity() - nextOffset;
    if (remainingLength >= HEADER_LENGTH) {
      buffer.putInt(lengthOffset(nextOffset), remainingLength);
      buffer.putShort(typeOffset(nextOffset), TYPE_PADDING);
    }
  }

  private void reset() {
    buffer.wrap(0, 0);
    onCompleteHandler = null;
  }
}
