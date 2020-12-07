/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.log;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import io.zeebe.dispatcher.BlockPeek;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.impl.backpressure.AlgorithmCfg;
import io.zeebe.logstreams.impl.backpressure.AppendBackpressureMetrics;
import io.zeebe.logstreams.impl.backpressure.AppendEntryLimiter;
import io.zeebe.logstreams.impl.backpressure.AppendLimiter;
import io.zeebe.logstreams.impl.backpressure.AppenderGradient2Cfg;
import io.zeebe.logstreams.impl.backpressure.AppenderVegasCfg;
import io.zeebe.logstreams.impl.backpressure.BackpressureConstants;
import io.zeebe.logstreams.impl.backpressure.NoopAppendLimiter;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.Environment;
import io.zeebe.util.collection.Tuple;
import io.zeebe.util.health.FailureListener;
import io.zeebe.util.health.HealthMonitorable;
import io.zeebe.util.health.HealthStatus;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.function.LongConsumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

/** Consume the write buffer and append the blocks to the distributedlog. */
public class LogStorageAppender extends Actor implements HealthMonitorable {

  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private static final Map<String, AlgorithmCfg> ALGORITHM_CFG =
      Map.of("vegas", new AppenderVegasCfg(), "gradient2", new AppenderGradient2Cfg());

  private final String name;
  private final Subscription writeBufferSubscription;
  private final int maxAppendBlockSize;
  private final LogStorage logStorage;
  private final AppendLimiter appendEntryLimiter;
  private final AppendBackpressureMetrics appendBackpressureMetrics;
  private final Environment env;
  private final LoggedEventImpl positionReader = new LoggedEventImpl();
  private final AppenderMetrics appenderMetrics;
  private FailureListener failureListener;
  private final ActorFuture<Void> closeFuture;
  private final LongConsumer commitPositionListener;

  public LogStorageAppender(
      final String name,
      final int partitionId,
      final LogStorage logStorage,
      final Subscription writeBufferSubscription,
      final int maxBlockSize,
      final LongConsumer commitPositionListener) {
    appenderMetrics = new AppenderMetrics(Integer.toString(partitionId));
    this.commitPositionListener = commitPositionListener;
    env = new Environment();
    this.name = name;
    this.logStorage = logStorage;
    this.writeBufferSubscription = writeBufferSubscription;
    maxAppendBlockSize = maxBlockSize;
    appendBackpressureMetrics = new AppendBackpressureMetrics(partitionId);

    final boolean isBackpressureEnabled =
        env.getBool(BackpressureConstants.ENV_BP_APPENDER).orElse(true);
    appendEntryLimiter =
        isBackpressureEnabled ? initBackpressure(partitionId) : initNoBackpressure(partitionId);
    closeFuture = new CompletableActorFuture<>();
  }

  private AppendLimiter initBackpressure(final int partitionId) {
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

  private AppendLimiter initNoBackpressure(final int partition) {
    LOG.warn(
        "No back pressure for the log appender (partition = {}) configured! This might cause problems.",
        partition);
    return new NoopAppendLimiter();
  }

  private void appendBlock(final BlockPeek blockPeek) {
    final ByteBuffer rawBuffer = blockPeek.getRawBuffer();
    final int bytes = rawBuffer.remaining();
    final ByteBuffer copiedBuffer = ByteBuffer.allocate(bytes).put(rawBuffer).flip();
    final Tuple<Long, Long> positions = readLowestHighestPosition(copiedBuffer);

    // Commit position is the position of the last event.
    appendBackpressureMetrics.newEntryToAppend();
    if (appendEntryLimiter.tryAcquire(positions.getRight())) {
      final var listener = new Listener(this, positions.getRight(), ActorClock.currentTimeMillis());
      logStorage.append(positions.getLeft(), positions.getRight(), copiedBuffer, listener);

      blockPeek.markCompleted();
    } else {
      appendBackpressureMetrics.deferred();
      LOG.trace(
          "Backpressure happens: in flight {} limit {}",
          appendEntryLimiter.getInflight(),
          appendEntryLimiter.getLimit());
      // we will be called later again
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  protected void onActorStarting() {
    actor.consume(writeBufferSubscription, this::onWriteBufferAvailable);
  }

  @Override
  protected void onActorClosed() {
    closeFuture.complete(null);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    if (actor.isClosed()) {
      return closeFuture;
    }
    super.closeAsync();
    return closeFuture;
  }

  @Override
  protected void handleFailure(final Exception failure) {
    onFailure(failure);
  }

  @Override
  public void onActorFailed() {
    closeFuture.complete(null);
  }

  private void onWriteBufferAvailable() {
    final BlockPeek blockPeek = new BlockPeek();
    if (writeBufferSubscription.peekBlock(blockPeek, maxAppendBlockSize, true) > 0) {
      appendBlock(blockPeek);
    } else {
      actor.yield();
    }
  }

  private Tuple<Long, Long> readLowestHighestPosition(final ByteBuffer buffer) {
    final var view = new UnsafeBuffer(buffer);
    final var positions = new Tuple<>(Long.MAX_VALUE, Long.MIN_VALUE);
    var offset = 0;

    do {
      positionReader.wrap(view, offset);
      final long pos = positionReader.getPosition();
      positions.setLeft(Math.min(positions.getLeft(), pos));
      positions.setRight(Math.max(positions.getRight(), pos));
      offset += positionReader.getLength();
    } while (offset < view.capacity());

    return positions;
  }

  @Override
  public HealthStatus getHealthStatus() {
    return actor.isClosed() ? HealthStatus.UNHEALTHY : HealthStatus.HEALTHY;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> this.failureListener = failureListener);
  }

  private void onFailure(final Throwable error) {
    LOG.error("Actor {} failed in phase {}.", name, actor.getLifecyclePhase(), error);
    actor.fail();
    if (failureListener != null) {
      failureListener.onFailure();
    }
  }

  void runOnFailure(final Throwable error) {
    actor.run(() -> onFailure(error));
  }

  void releaseBackPressure(final long highestPosition) {
    actor.run(() -> appendEntryLimiter.onCommit(highestPosition));
  }

  void notifyWritePosition(final long highestPosition, final long startTime) {
    actor.run(
        () -> {
          appenderMetrics.setLastAppendedPosition(highestPosition);
          appenderMetrics.appendLatency(startTime, ActorClock.currentTimeMillis());
        });
  }

  void notifyCommitPosition(final long highestPosition, final long startTime) {
    actor.run(
        () -> {
          commitPositionListener.accept(highestPosition);
          appenderMetrics.setLastCommittedPosition(highestPosition);
          appenderMetrics.commitLatency(startTime, ActorClock.currentTimeMillis());
        });
  }
}
