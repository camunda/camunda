/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.dispatcher;

import static io.zeebe.dispatcher.impl.PositionUtil.partitionId;
import static io.zeebe.dispatcher.impl.PositionUtil.partitionOffset;
import static io.zeebe.dispatcher.impl.PositionUtil.position;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.HEADER_LENGTH;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.TYPE_PADDING;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagBatchBegin;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagBatchEnd;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagFailed;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.flagsOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageLength;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.messageOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.streamIdOffset;
import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.typeOffset;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.dispatcher.impl.log.LogBuffer;
import io.zeebe.dispatcher.impl.log.LogBufferPartition;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.channel.ConsumableChannel;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class Subscription implements ConsumableChannel {
  public static final Logger LOG = Loggers.DISPATCHER_LOGGER;

  protected final ActorConditions actorConditions = new ActorConditions();

  protected final AtomicPosition limit;
  protected final AtomicPosition position;
  protected final LogBuffer logBuffer;
  protected final int id;
  protected final String name;
  protected final ActorCondition dataConsumed;
  protected final ByteBuffer rawDispatcherBufferView;

  protected volatile boolean isClosed = false;

  public Subscription(
      final AtomicPosition position,
      final AtomicPosition limit,
      final int id,
      final String name,
      final ActorCondition onConsumption,
      final LogBuffer logBuffer) {
    this.position = position;
    this.id = id;
    this.name = name;
    this.limit = limit;
    this.logBuffer = logBuffer;
    dataConsumed = onConsumption;

    // required so that a subscription can freely modify position and limit of the raw buffer
    rawDispatcherBufferView = logBuffer.createRawBufferView();
  }

  public long getPosition() {
    return position.get();
  }

  @Override
  public boolean hasAvailable() {
    return getLimit() > getPosition();
  }

  @Override
  public void registerConsumer(final ActorCondition consumer) {
    actorConditions.registerConsumer(consumer);
  }

  @Override
  public void removeConsumer(final ActorCondition consumer) {
    actorConditions.registerConsumer(consumer);
  }

  protected long getLimit() {
    return limit.get();
  }

  /**
   * Read fragments from the buffer and invoke the given handler for each fragment. Consume the
   * fragments (i.e. update the subscription position) after all fragments are handled.
   *
   * <p>Note that the handler is not aware of fragment batches.
   *
   * @return the amount of read fragments
   */
  public int poll(final FragmentHandler frgHandler, final int maxNumOfFragments) {
    int fragmentsRead = 0;

    if (!isClosed) {
      final long currentPosition = position.get();
      final long limit = getLimit();

      if (limit > currentPosition) {
        final int partitionId = partitionId(currentPosition);
        final int partitionOffset = partitionOffset(currentPosition);
        final LogBufferPartition partition = logBuffer.getPartition(partitionId);

        fragmentsRead =
            pollFragments(
                partition,
                frgHandler,
                partitionId,
                partitionOffset,
                maxNumOfFragments,
                limit,
                false);
      }
    }

    return fragmentsRead;
  }

  protected int pollFragments(
      final LogBufferPartition partition,
      final FragmentHandler frgHandler,
      int partitionId,
      int fragmentOffset,
      final int maxNumOfFragments,
      final long limit,
      final boolean handlerControlled) {
    final UnsafeBuffer buffer = partition.getDataBuffer();

    int fragmentsConsumed = 0;

    int fragmentResult = FragmentHandler.CONSUME_FRAGMENT_RESULT;
    do {
      final int framedLength = buffer.getIntVolatile(lengthOffset(fragmentOffset));
      if (framedLength <= 0) {
        break;
      }

      final short type = buffer.getShort(typeOffset(fragmentOffset));
      if (type == TYPE_PADDING) {
        fragmentOffset += alignedLength(framedLength);

        if (fragmentOffset >= partition.getPartitionSize()) {
          ++partitionId;
          fragmentOffset = 0;
          break;
        }
      } else {
        final int streamId = buffer.getInt(streamIdOffset(fragmentOffset));
        final int flagsOffset = flagsOffset(fragmentOffset);
        final byte flags = buffer.getByte(flagsOffset);
        try {
          final boolean isMarkedAsFailed = flagFailed(flags);

          final int messageLength = messageLength(framedLength);
          final int handlerResult =
              frgHandler.onFragment(
                  buffer, messageOffset(fragmentOffset), messageLength, streamId, isMarkedAsFailed);

          if (handlerResult == FragmentHandler.FAILED_FRAGMENT_RESULT && !isMarkedAsFailed) {
            buffer.putByte(flagsOffset, DataFrameDescriptor.enableFlagFailed(flags));
          }

          if (handlerControlled) {
            fragmentResult = handlerResult;
          }

        } catch (final RuntimeException e) {
          // TODO!
          LOG.error("Failed to handle fragment", e);
        }

        if (fragmentResult != FragmentHandler.POSTPONE_FRAGMENT_RESULT) {
          ++fragmentsConsumed;
          fragmentOffset += alignedLength(framedLength);
        }
      }
    } while (fragmentResult != FragmentHandler.POSTPONE_FRAGMENT_RESULT
        && fragmentsConsumed < maxNumOfFragments
        && position(partitionId, fragmentOffset) < limit);

    position.set(position(partitionId, fragmentOffset));
    dataConsumed.signal();

    return fragmentsConsumed;
  }

  /**
   * Sequentially read fragments from the buffer and invoke the given handler for each fragment.
   * Consume the fragments (i.e. update the subscription position) depending on the return value of
   * {@link FragmentHandler#onFragment(org.agrona.DirectBuffer, int, int, int, boolean)}. If a
   * fragment is not consumed then no following fragments are read.
   *
   * <p>Note that the handler is not aware of fragment batches.
   *
   * @return the amount of read fragments
   */
  public int peekAndConsume(final FragmentHandler frgHandler, final int maxNumOfFragments) {
    int fragmentsRead = 0;

    if (!isClosed) {
      final long currentPosition = position.get();
      final long limit = getLimit();

      if (limit > currentPosition) {
        final int partitionId = partitionId(currentPosition);
        final int partitionOffset = partitionOffset(currentPosition);

        final LogBufferPartition partition = logBuffer.getPartition(partitionId);

        fragmentsRead =
            pollFragments(
                partition,
                frgHandler,
                partitionId,
                partitionOffset,
                maxNumOfFragments,
                limit,
                true);
      }
    }

    return fragmentsRead;
  }

  /**
   * Read fragments from the buffer as block. Use {@link BlockPeek#getBuffer()} to consume the
   * fragments and finish the operation using {@link BlockPeek#markCompleted()} or {@link
   * BlockPeek#markFailed()}.
   *
   * <p>Note that the block only contains complete fragment batches.
   *
   * @param isStreamAware if <code>true</code>, it stops reading fragments when a fragment has a
   *     different stream id than the previous one
   * @return amount of read bytes
   */
  public int peekBlock(
      final BlockPeek availableBlock, final int maxBlockSize, final boolean isStreamAware) {
    int bytesAvailable = 0;

    if (!isClosed) {
      final long currentPosition = position.get();

      final long limit = getLimit();

      if (limit > currentPosition) {
        final int partitionId = partitionId(currentPosition);
        final int partitionOffset = partitionOffset(currentPosition);

        final LogBufferPartition partition = logBuffer.getPartition(partitionId);

        bytesAvailable =
            peekBlock(
                partition,
                availableBlock,
                partitionId,
                partitionOffset,
                maxBlockSize,
                limit,
                isStreamAware);
      }
    }

    return bytesAvailable;
  }

  protected int peekBlock(
      final LogBufferPartition partition,
      final BlockPeek availableBlock,
      int partitionId,
      int partitionOffset,
      final int maxBlockSize,
      final long limit,
      final boolean isStreamAware) {
    final UnsafeBuffer buffer = partition.getDataBuffer();
    final int bufferOffset = partition.getUnderlyingBufferOffset();
    final int firstFragmentOffset = partitionOffset;

    int readBytes = 0;
    int initialStreamId = -1;
    boolean isReadingBatch = false;
    int offset = partitionOffset;

    int offsetLimit = partitionOffset(limit);
    if (partitionId(limit) > partitionId) {
      offsetLimit = partition.getPartitionSize();
    }

    do {
      final int framedLength = buffer.getIntVolatile(lengthOffset(partitionOffset));
      if (framedLength <= 0) {
        break;
      }

      final short type = buffer.getShort(typeOffset(partitionOffset));
      if (type == TYPE_PADDING) {
        partitionOffset += alignedLength(framedLength);

        if (partitionOffset >= partition.getPartitionSize()) {
          partitionId += 1;
          partitionOffset = 0;
        }
        offset = partitionOffset;

        if (readBytes == 0) {
          position.proposeMaxOrdered(position(partitionId, partitionOffset));
          dataConsumed.signal();
        }

        break;
      } else {

        if (isStreamAware) {
          final int streamId = buffer.getInt(streamIdOffset(partitionOffset));
          if (readBytes == 0) {
            initialStreamId = streamId;
          } else if (streamId != initialStreamId) {
            break;
          }
        }

        final byte flags = buffer.getByte(flagsOffset(partitionOffset));
        if (!isReadingBatch) {
          isReadingBatch = flagBatchBegin(flags);
        } else {
          isReadingBatch = !flagBatchEnd(flags);
        }

        final int alignedFrameLength = alignedLength(framedLength);
        if (alignedFrameLength <= maxBlockSize - readBytes) {
          partitionOffset += alignedFrameLength;
          readBytes += alignedFrameLength;

          if (!isReadingBatch) {
            offset = partitionOffset;
          }
        } else {
          break;
        }
      }
    } while (maxBlockSize - readBytes > HEADER_LENGTH && partitionOffset < offsetLimit);

    // only read complete fragment batches
    final int blockLength = readBytes + offset - partitionOffset;
    if (blockLength > 0) {
      final int absoluteOffset = bufferOffset + firstFragmentOffset;

      availableBlock.setBlock(
          rawDispatcherBufferView,
          position,
          dataConsumed,
          initialStreamId,
          absoluteOffset,
          blockLength,
          partitionId,
          offset);
    }
    return blockLength;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  protected ActorConditions getActorConditions() {
    return actorConditions;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Subscription [id=");
    builder.append(id);
    builder.append(", name=");
    builder.append(name);
    builder.append("]");
    return builder.toString();
  }
}
