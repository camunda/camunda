/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import com.netflix.concurrency.limits.limit.WindowedLimit;
import io.camunda.zeebe.dispatcher.BlockPeek;
import io.camunda.zeebe.dispatcher.Subscription;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.impl.backpressure.AlgorithmCfg;
import io.camunda.zeebe.logstreams.impl.backpressure.AppendBackpressureMetrics;
import io.camunda.zeebe.logstreams.impl.backpressure.AppendEntryLimiter;
import io.camunda.zeebe.logstreams.impl.backpressure.AppendLimiter;
import io.camunda.zeebe.logstreams.impl.backpressure.AppenderGradient2Cfg;
import io.camunda.zeebe.logstreams.impl.backpressure.AppenderVegasCfg;
import io.camunda.zeebe.logstreams.impl.backpressure.BackpressureConstants;
import io.camunda.zeebe.logstreams.impl.backpressure.NoopAppendLimiter;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.util.Environment;
import io.camunda.zeebe.util.collection.Tuple;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import io.prometheus.client.Histogram.Timer;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final ActorFuture<Void> closeFuture;
  private final int partitionId;

  public LogStorageAppender(
      final String name,
      final int partitionId,
      final LogStorage logStorage,
      final Subscription writeBufferSubscription,
      final int maxBlockSize) {
    appenderMetrics = new AppenderMetrics(Integer.toString(partitionId));
    env = new Environment();
    this.name = name;
    this.partitionId = partitionId;
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
      final var listener =
          new Listener(
              this,
              positions.getRight(),
              appenderMetrics.startAppendLatencyTimer(),
              appenderMetrics.startCommitLatencyTimer());
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
  protected Map<String, String> createContext() {
    final var context = super.createContext();
    context.put(ACTOR_PROP_PARTITION_ID, Integer.toString(partitionId));
    return context;
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
      actor.yieldThread();
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
    actor.run(() -> failureListeners.add(failureListener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.remove(failureListener));
  }

  private void onFailure(final Throwable error) {
    LOG.error("Actor {} failed in phase {}.", name, actor.getLifecyclePhase(), error);
    actor.fail();
    failureListeners.forEach(FailureListener::onFailure);
  }

  void runOnFailure(final Throwable error) {
    actor.run(() -> onFailure(error));
  }

  void releaseBackPressure(final long highestPosition) {
    actor.run(() -> appendEntryLimiter.onCommit(highestPosition));
  }

  void notifyWritePosition(final long highestPosition, final Timer appendLatencyTimer) {
    actor.run(
        () -> {
          appenderMetrics.setLastAppendedPosition(highestPosition);
          // observe append latency
          appendLatencyTimer.close();
        });
  }

  void notifyCommitPosition(final long highestPosition, final Timer commitLatencyTimer) {
    actor.run(
        () -> {
          appenderMetrics.setLastCommittedPosition(highestPosition);
          // observe commit latency
          commitLatencyTimer.close();
        });
  }
}
