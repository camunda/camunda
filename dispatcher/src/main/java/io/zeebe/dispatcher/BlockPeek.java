/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher;

import static io.zeebe.dispatcher.impl.PositionUtil.position;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.util.TriConsumer;
import io.zeebe.util.sched.ActorCondition;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Represents a block of fragments to read from. */
public class BlockPeek implements Iterable<DirectBuffer> {

  protected int streamId;
  protected int blockLength;
  protected final DataFrameIterator iterator = new DataFrameIterator();
  private ByteBuffer byteBuffer;
  private final UnsafeBuffer bufferView = new UnsafeBuffer(0, 0);
  private AtomicPosition subscriberPosition;
  private int bufferOffset;
  private int newPartitionId;
  private int newPartitionOffset;
  private ActorCondition dataConsumed;
  private Queue<TriConsumer<ZeebeEntry, Long, Integer>> handlers = new ConcurrentLinkedQueue<>();

  public void setBlock(
      final ByteBuffer byteBuffer,
      final AtomicPosition position,
      final ActorCondition dataConsumed,
      final int streamId,
      final int bufferOffset,
      final int blockLength,
      final int newPartitionId,
      final int newPartitionOffset) {
    this.byteBuffer = byteBuffer;
    this.subscriberPosition = position;
    this.dataConsumed = dataConsumed;
    this.streamId = streamId;
    this.bufferOffset = bufferOffset;
    this.blockLength = blockLength;
    this.newPartitionId = newPartitionId;
    this.newPartitionOffset = newPartitionOffset;

    byteBuffer.limit(bufferOffset + blockLength);
    byteBuffer.position(bufferOffset);

    bufferView.wrap(byteBuffer, bufferOffset, blockLength);
  }

  public ByteBuffer getRawBuffer() {
    return byteBuffer;
  }

  /** Returns the buffer to read from. */
  public MutableDirectBuffer getBuffer() {
    return bufferView;
  }

  /**
   * Finish reading and consume the fragments (i.e. update the subscription position). Mark all
   * fragments as failed.
   */
  public void markFailed() {
    int fragmentOffset = 0;
    while (fragmentOffset < blockLength) {
      int framedFragmentLength =
          bufferView.getInt(DataFrameDescriptor.lengthOffset(fragmentOffset));

      if (framedFragmentLength < 0) {
        framedFragmentLength = -framedFragmentLength;
      }

      final int frameLength = DataFrameDescriptor.alignedLength(framedFragmentLength);
      final int flagsOffset = DataFrameDescriptor.flagsOffset(fragmentOffset);
      final byte flags = bufferView.getByte(flagsOffset);

      bufferView.putByte(flagsOffset, DataFrameDescriptor.enableFlagFailed(flags));

      fragmentOffset += frameLength;
    }

    updatePosition();
  }

  /** Finish reading and consume the fragments (i.e. update the subscription position). */
  public void markCompleted() {
    updatePosition();
  }

  private void updatePosition() {
    subscriberPosition.proposeMaxOrdered(position(newPartitionId, newPartitionOffset));
    dataConsumed.signal();
  }

  public int getStreamId() {
    return streamId;
  }

  public int getBufferOffset() {
    return bufferOffset;
  }

  public int getBlockLength() {
    return blockLength;
  }

  @Override
  public Iterator<DirectBuffer> iterator() {
    iterator.reset();
    return iterator;
  }

  public Queue<TriConsumer<ZeebeEntry, Long, Integer>> getHandlers() {
    return handlers;
  }

  public void addHandler(final TriConsumer<ZeebeEntry, Long, Integer> handler) {
    handlers.add(handler);
  }

  protected class DataFrameIterator implements Iterator<DirectBuffer> {

    protected int iterationOffset;
    protected final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

    public void reset() {
      iterationOffset = 0;
    }

    @Override
    public boolean hasNext() {
      return iterationOffset < blockLength;
    }

    @Override
    public DirectBuffer next() {
      final int framedFragmentLength =
          bufferView.getInt(DataFrameDescriptor.lengthOffset(iterationOffset));
      final int fragmentLength = DataFrameDescriptor.messageLength(framedFragmentLength);
      final int messageOffset = DataFrameDescriptor.messageOffset(iterationOffset);

      buffer.wrap(bufferView, messageOffset, fragmentLength);

      iterationOffset += DataFrameDescriptor.alignedLength(framedFragmentLength);

      return buffer;
    }
  }
}
