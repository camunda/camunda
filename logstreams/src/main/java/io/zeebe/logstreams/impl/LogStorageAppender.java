/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.impl.backpressure.AlgorithmCfg;
import io.zeebe.logstreams.impl.backpressure.AppendBackpressureMetrics;
import io.zeebe.logstreams.impl.backpressure.AppendEntryLimiter;
import io.zeebe.logstreams.impl.backpressure.AppendLimiter;
import io.zeebe.logstreams.impl.backpressure.AppenderGradient2Cfg;
import io.zeebe.logstreams.impl.backpressure.AppenderVegasCfg;
import io.zeebe.logstreams.impl.backpressure.BackpressureConstants;
import io.zeebe.logstreams.impl.backpressure.NoopAppendLimiter;
import io.zeebe.util.Environment;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/** Consume the write buffer and append the blocks to the distributedlog. */
public class LogStorageAppender extends Actor {

  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private static final Map<String, AlgorithmCfg> ALGORITHM_CFG =
      Map.of("vegas", new AppenderVegasCfg(), "gradient2", new AppenderGradient2Cfg());
  private final AtomicBoolean isFailed = new AtomicBoolean(false);

  private final BlockPeek blockPeek = new BlockPeek();
  private final String name;
  private final Subscription writeBufferSubscription;
  private final int maxAppendBlockSize;
  private final DistributedLogstreamPartition distributedLog;
  private final AppendLimiter appendEntryLimiter;
  private final AppendBackpressureMetrics appendBackpressureMetrics;
  private final Environment env;
  private long currentInFlightBytes;
  private final Runnable peekedBlockHandler = this::appendBlock;

  public LogStorageAppender(
      String name,
      DistributedLogstreamPartition distributedLog,
      Subscription writeBufferSubscription,
      int maxBlockSize) {
    this.env = new Environment();
    this.name = name;
    this.distributedLog = distributedLog;
    this.writeBufferSubscription = writeBufferSubscription;
    this.maxAppendBlockSize = maxBlockSize;

    final int partitionId = distributedLog.getPartitionId();
    appendBackpressureMetrics = new AppendBackpressureMetrics(partitionId);

    final boolean isBackpressureEnabled =
        env.getBool(BackpressureConstants.ENV_BP_APPENDER).orElse(true);
    appendEntryLimiter =
        isBackpressureEnabled ? initBackpressure(partitionId) : initNoBackpressure(partitionId);
  }

  private AppendLimiter initBackpressure(int partitionId) {
    final String algorithmName =
        env.get(BackpressureConstants.ENV_BP_APPENDER_ALGORITHM).orElse("vegas").toLowerCase();
    final AlgorithmCfg algorithmCfg =
        ALGORITHM_CFG.getOrDefault(algorithmName, new AppenderVegasCfg());
    algorithmCfg.applyEnvironment(env);

    final AbstractLimit abstractLimit = algorithmCfg.get();
    final boolean windowedLimiter =
        env.getBool(BackpressureConstants.ENV_BP_APPENDER_WINDOWED).orElse(false);

    LOG.debug(
        "Configured log appender back pressure at partition {} as {}. Window limiting is {}",
        partitionId,
        algorithmCfg,
        windowedLimiter ? "enabled" : "disabled");
    return AppendEntryLimiter.builder()
        .limit(windowedLimiter ? WindowedLimit.newBuilder().build(abstractLimit) : abstractLimit)
        .partitionId(partitionId)
        .build();
  }

  private AppendLimiter initNoBackpressure(int partition) {
    LOG.warn(
        "No back pressure for the log appender (partition = {}) configured! This might cause problems.",
        partition);
    return new NoopAppendLimiter();
  }

  private void appendBlock() {
    final ByteBuffer rawBuffer = blockPeek.getRawBuffer();
    final int bytes = rawBuffer.remaining();
    final var bytesToAppend = new byte[bytes];
    rawBuffer.get(bytesToAppend);

    // Commit position is the position of the last event. DistributedLogstream uses this position
    // to identify duplicate append requests during recovery.
    final long lastEventPosition = getLastEventPosition(bytesToAppend);
    appendBackpressureMetrics.newEntryToAppend();
    if (appendEntryLimiter.tryAcquire(lastEventPosition)) {
      currentInFlightBytes += bytes;
      appendToPrimitive(bytesToAppend, lastEventPosition);
      blockPeek.markCompleted();
    } else {
      appendBackpressureMetrics.deferred();
      LOG.trace(
          "Backpressure happens: in flight {} (in bytes {}) limit {}",
          appendEntryLimiter.getInflight(),
          currentInFlightBytes,
          appendEntryLimiter.getLimit());
      actor.yield();
    }
  }

  private void appendToPrimitive(byte[] bytesToAppend, long lastEventPosition) {
    actor.submit(
        () -> {
          distributedLog
              .asyncAppend(bytesToAppend, lastEventPosition)
              .whenComplete(
                  (appendPosition, error) -> {
                    if (error != null) {
                      LOG.error(
                          "Failed to append block with last event position {}, retry.",
                          lastEventPosition);
                      appendToPrimitive(bytesToAppend, lastEventPosition);
                    } else {
                      actor.run(
                          () -> {
                            appendEntryLimiter.onCommit(lastEventPosition);
                            currentInFlightBytes -= bytesToAppend.length;
                            actor.run(this::peekBlock);
                          });
                    }
                  });
        });
  }

  /* Iterate over the events in buffer and find the position of the last event */
  private long getLastEventPosition(byte[] buffer) {
    int bufferOffset = 0;
    final DirectBuffer directBuffer = new UnsafeBuffer(0, 0);

    directBuffer.wrap(buffer);
    long lastEventPosition = -1;

    final LoggedEventImpl nextEvent = new LoggedEventImpl();
    int remaining = buffer.length - bufferOffset;
    while (remaining > 0) {
      nextEvent.wrap(directBuffer, bufferOffset);
      bufferOffset += nextEvent.getFragmentLength();
      lastEventPosition = nextEvent.getPosition();
      remaining = buffer.length - bufferOffset;
    }
    return lastEventPosition;
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }

  public long getCurrentAppenderPosition() {
    return writeBufferSubscription.getPosition();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  protected void onActorStarting() {

    actor.consume(writeBufferSubscription, this::peekBlock);
  }

  private void peekBlock() {
    if (writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true) > 0) {
      peekedBlockHandler.run();
    } else {
      actor.yield();
    }
  }

  public boolean isFailed() {
    return isFailed.get();
  }
}
