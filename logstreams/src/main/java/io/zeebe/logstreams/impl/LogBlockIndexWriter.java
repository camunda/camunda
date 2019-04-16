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
package io.zeebe.logstreams.impl;

import static io.zeebe.logstreams.impl.LogEntryDescriptor.getPosition;
import static io.zeebe.logstreams.impl.service.LogStreamService.INVALID_ADDRESS;
import static io.zeebe.logstreams.spi.LogStorage.OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY;
import static io.zeebe.logstreams.spi.LogStorage.OP_RESULT_INVALID_ADDR;

import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.impl.log.index.LogBlockIndexContext;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocators;
import io.zeebe.util.metrics.Metric;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import java.nio.ByteBuffer;
import java.time.Duration;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.Position;
import org.slf4j.Logger;

/** Read committed events from the log storage and append them to the block index. */
public class LogBlockIndexWriter extends Actor {
  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  /**
   * The default deviation is 10%. That means for blocks which are filled 90% a block index will be
   * created.
   */
  public static final float DEFAULT_DEVIATION = 0.1f;

  private Runnable currentRunnable;

  private final Runnable runCurrentWork = this::runCurrentWork;
  private final Runnable readLogStorage = this::readLogStorage;
  private final Runnable addCurrentBlockToIndex = this::addCurrentBlockToIndex;
  private final Runnable createSnapshot = this::createSnapshot;

  private final String name;
  private final LogStorage logStorage;
  private final LogBlockIndex blockIndex;
  private final LogBlockIndexContext indexContext;
  private final MetricsManager metricsManager;

  /** Defines the block size for which an index will be created. */
  private final int indexBlockSize;

  /**
   * The deviation which will be used in calculation of the index block size. It defines the
   * allowable tolerance. That means if the deviation is set to 0.1f (10%), an index will be created
   * if the block is 90 % filled.
   */
  private final float deviation;

  private final CompleteEventsInBlockProcessor completeEventsProcessor =
      new CompleteEventsInBlockProcessor();

  private long nextAddress = INVALID_ADDRESS;

  private int currentBlockSize = 0;
  private long currentBlockAddress = INVALID_ADDRESS;
  private long currentBlockEventPosition = 0;

  private long lastBlockAddress = 0;
  private long lastBlockEventPosition = 0;

  private final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
  private int bufferSize;
  private ByteBuffer ioBuffer;
  private AllocatedBuffer allocatedBuffer;

  private final Position commitPosition;
  private final ActorConditions onCommitPositionUpdatedConditions;
  private ActorCondition onCommitCondition;

  private final Duration snapshotInterval;
  private long snapshotEventPosition = -1;
  private final int maxSnapshots;

  private Metric snapshotsCreated;

  public LogBlockIndexWriter(
      String name,
      LogStreamBuilder builder,
      LogStorage logStorage,
      LogBlockIndex blockIndex,
      MetricsManager metricsManager) {
    this.name = name;
    this.logStorage = logStorage;
    this.blockIndex = blockIndex;
    this.metricsManager = metricsManager;
    this.commitPosition = builder.getCommitPosition();
    this.onCommitPositionUpdatedConditions = builder.getOnCommitPositionUpdatedConditions();

    this.deviation = builder.getDeviation();
    this.indexBlockSize = (int) (builder.getIndexBlockSize() * (1f - deviation));
    this.snapshotInterval = builder.getSnapshotPeriod();
    this.maxSnapshots = builder.getMaxSnapshots();
    this.bufferSize = builder.getReadBlockSize();

    this.allocatedBuffer = BufferAllocators.allocateDirect(bufferSize);
    this.ioBuffer = allocatedBuffer.getRawBuffer();
    this.buffer.wrap(ioBuffer);

    this.indexContext = blockIndex.createLogBlockIndexContext();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  protected void onActorStarting() {
    snapshotsCreated =
        metricsManager
            .newMetric("logstream_blockidx_snapshots")
            .type("counter")
            .label("logName", getName())
            .create();

    try {
      final long snapshotPosition = blockIndex.getLastPosition();
      final long snapshotBlockAddress =
          blockIndex.lookupBlockAddress(indexContext, snapshotPosition);

      if (snapshotBlockAddress >= logStorage.getFirstBlockAddress()) {
        nextAddress = snapshotBlockAddress;
        lastBlockAddress = snapshotBlockAddress;
        lastBlockEventPosition = snapshotPosition;
        snapshotEventPosition = snapshotPosition;
      } else {
        LOG.warn("Can't find address of snapshot position. Rebuilding block index.");
      }

      if (nextAddress == INVALID_ADDRESS) {
        nextAddress = logStorage.getFirstBlockAddress();
        lastBlockAddress = 0;
      }
    } catch (Exception e) {
      LOG.error("Failed to recover block index", e);
      throw new RuntimeException("Failed to recover block index", e);
    }
  }

  @Override
  protected void onActorStarted() {
    this.onCommitCondition = actor.onCondition("log-index-on-commit", runCurrentWork);
    onCommitPositionUpdatedConditions.registerConsumer(onCommitCondition);

    actor.runAtFixedRate(snapshotInterval, createSnapshot);

    if (nextAddress > 0) {
      currentRunnable = readLogStorage;
      runCurrentWork();
    } else {
      // the log is empty
      currentRunnable =
          () -> {
            // when the first position is committed
            // - then start reading on the head of the log
            nextAddress = logStorage.getFirstBlockAddress();

            currentRunnable = readLogStorage;
            runCurrentWork();
          };
    }
  }

  private void runCurrentWork() {
    actor.submit(currentRunnable);
  }

  private void readLogStorage() {
    ioBuffer.clear();

    final long currentAddress = nextAddress;
    final long result = logStorage.read(ioBuffer, currentAddress, completeEventsProcessor);

    if (result > currentAddress) {
      nextAddress = result;

      addToCurrentBlock(currentAddress, ioBuffer.position());

    } else if (result == OP_RESULT_INSUFFICIENT_BUFFER_CAPACITY) {
      increaseBufferSize();
      runCurrentWork();
    } else if (result == OP_RESULT_INVALID_ADDR) {
      LOG.warn("Can't read from illegal address: {}", currentAddress);

      nextAddress = lastBlockAddress;
      resetCurrentBlock();
    }
  }

  private void addToCurrentBlock(long currentAddress, int readBytes) {
    if (currentBlockAddress == INVALID_ADDRESS) {
      currentBlockAddress = currentAddress;
      currentBlockEventPosition = getPosition(buffer, 0);
    }

    currentBlockSize += readBytes;

    if (currentBlockSize >= indexBlockSize) {
      addCurrentBlockToIndex();
    } else {
      // block is not filled enough
      // - read more events into buffer
      runCurrentWork();
    }
  }

  private void addCurrentBlockToIndex() {
    if (isCurrentBlockCommitted()) {
      if (currentBlockAddress > lastBlockAddress) {
        LOG.trace(
            "Add block to index with position {} and address {}.",
            currentBlockEventPosition,
            currentBlockAddress);

        blockIndex.addBlock(indexContext, currentBlockEventPosition, currentBlockAddress);

        lastBlockAddress = currentBlockAddress;
        lastBlockEventPosition = currentBlockEventPosition;
      }

      resetCurrentBlock();

      currentRunnable = readLogStorage;
    } else {
      // try again when commit position is updated
      currentRunnable = addCurrentBlockToIndex;
    }

    runCurrentWork();
  }

  private boolean isCurrentBlockCommitted() {
    return commitPosition.getVolatile() >= completeEventsProcessor.getLastReadEventPosition();
  }

  private void resetCurrentBlock() {
    currentBlockAddress = INVALID_ADDRESS;
    currentBlockEventPosition = 0;
    currentBlockSize = 0;
  }

  private void increaseBufferSize() {
    bufferSize *= 2;

    allocatedBuffer.close();

    allocatedBuffer = BufferAllocators.allocateDirect(bufferSize);
    ioBuffer = allocatedBuffer.getRawBuffer();
    buffer.wrap(ioBuffer);
  }

  private void createSnapshot() {
    try {
      if (lastBlockEventPosition > 0 && lastBlockEventPosition > snapshotEventPosition) {
        // flush the log to ensure that the snapshot doesn't contains indexes of unwritten events
        logStorage.flush();

        snapshotEventPosition = lastBlockEventPosition;
        blockIndex.writeSnapshot(snapshotEventPosition, maxSnapshots);

        LOG.trace("Created snapshot of block index {}.", name);
        snapshotsCreated.incrementOrdered();
      }
    } catch (Exception e) {
      LOG.warn("Failed to create snapshot of block index {}", name, e);
    }
  }

  public ActorFuture<Void> closeAsync() {
    return actor.close();
  }

  @Override
  protected void onActorClosing() {
    resetCurrentBlock();
    allocatedBuffer.close();
    onCommitPositionUpdatedConditions.removeConsumer(onCommitCondition);
    snapshotsCreated.close();
  }

  public Metric getSnapshotsCreated() {
    return snapshotsCreated;
  }
}
