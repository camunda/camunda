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
package io.zeebe.dispatcher;

import static io.zeebe.dispatcher.impl.PositionUtil.position;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.util.metrics.Metric;
import io.zeebe.util.sched.ActorCondition;
import java.nio.ByteBuffer;
import java.util.Iterator;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** Represents a block of fragments to read from. */
public class BlockPeek implements Iterable<DirectBuffer> {
  protected ByteBuffer byteBuffer;
  protected UnsafeBuffer bufferView = new UnsafeBuffer(0, 0);
  protected AtomicPosition subscriberPosition;

  protected int streamId;

  protected int bufferOffset;
  protected int blockLength;

  protected int newPartitionId;
  protected int newPartitionOffset;

  protected DataFrameIterator iterator = new DataFrameIterator();
  private ActorCondition dataConsumed;
  private int fragmentCount;
  private Metric fragmentsConsumedMetric;

  public void setBlock(
      final ByteBuffer byteBuffer,
      final AtomicPosition position,
      final ActorCondition dataConsumed,
      final int streamId,
      final int bufferOffset,
      final int blockLength,
      final int newPartitionId,
      final int newPartitionOffset,
      int fragmentCount,
      Metric fragmentsConsumedMetric) {
    this.byteBuffer = byteBuffer;
    this.subscriberPosition = position;
    this.dataConsumed = dataConsumed;
    this.streamId = streamId;
    this.bufferOffset = bufferOffset;
    this.blockLength = blockLength;
    this.newPartitionId = newPartitionId;
    this.newPartitionOffset = newPartitionOffset;
    this.fragmentCount = fragmentCount;
    this.fragmentsConsumedMetric = fragmentsConsumedMetric;

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

  /** Returns the position of the next block if this block was marked completed. */
  public long getNextPosition() {
    final long newPosition = position(newPartitionId, newPartitionOffset);
    if (subscriberPosition.get() < newPosition) {
      return newPosition;
    } else {
      return subscriberPosition.get();
    }
  }

  protected void updatePosition() {
    fragmentsConsumedMetric.getAndAddOrdered(fragmentCount);
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

  public long getBlockPosition() {
    return position(newPartitionId, newPartitionOffset);
  }

  @Override
  public Iterator<DirectBuffer> iterator() {
    iterator.reset();
    return iterator;
  }

  protected class DataFrameIterator implements Iterator<DirectBuffer> {

    protected int iterationOffset;
    protected UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

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
