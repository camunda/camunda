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
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.impl.backpressure.AlgorithmCfg;
import io.camunda.zeebe.logstreams.impl.backpressure.AppendBackpressureMetrics;
import io.camunda.zeebe.logstreams.impl.backpressure.AppendEntryLimiter;
import io.camunda.zeebe.logstreams.impl.backpressure.AppendLimiter;
import io.camunda.zeebe.logstreams.impl.backpressure.AppenderGradient2Cfg;
import io.camunda.zeebe.logstreams.impl.backpressure.AppenderVegasCfg;
import io.camunda.zeebe.logstreams.impl.backpressure.BackpressureConstants;
import io.camunda.zeebe.logstreams.impl.backpressure.NoopAppendLimiter;
import io.camunda.zeebe.logstreams.impl.serializer.SequencedBatchSerializer;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Environment;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.prometheus.client.Histogram.Timer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/** Consume the write buffer and append the blocks to the distributedlog. */
final class LogStorageAppender extends Actor implements HealthMonitorable {

  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private static final Map<String, AlgorithmCfg> ALGORITHM_CFG =
      Map.of("vegas", new AppenderVegasCfg(), "gradient2", new AppenderGradient2Cfg());

  private final String name;
  private final Sequencer sequencer;
  private final SequencedBatchSerializer serializer = new SequencedBatchSerializer();
  private final LogStorage logStorage;
  private final AppendLimiter appendEntryLimiter;
  private final AppendBackpressureMetrics appendBackpressureMetrics;
  private final Environment env;
  private final AppenderMetrics appenderMetrics;
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final ActorFuture<Void> closeFuture;
  private final int partitionId;

  LogStorageAppender(
      final String name,
      final int partitionId,
      final LogStorage logStorage,
      final Sequencer sequencer) {
    appenderMetrics = new AppenderMetrics(Integer.toString(partitionId));
    env = new Environment();
    this.name = name;
    this.partitionId = partitionId;
    this.logStorage = logStorage;
    this.sequencer = sequencer;
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
    sequencer.registerConsumer(actor.onCondition("sequencer", this::tryWriteBatch));
    actor.submit(this::tryWriteBatch);
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
  protected void handleFailure(final Throwable failure) {
    onFailure(failure);
  }

  @Override
  public void onActorFailed() {
    closeFuture.complete(null);
  }

  @Override
  public HealthReport getHealthReport() {
    return actor.isClosed()
        ? HealthReport.unhealthy(this).withMessage("actor is closed")
        : HealthReport.healthy(this);
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.add(failureListener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.remove(failureListener));
  }

  private void tryWriteBatch() {
    final var peek = sequencer.peek();
    if (peek == null) {
      // wait for signal from sequencer
      return;
    }
    appendBackpressureMetrics.newEntryToAppend();

    final var highestPosition = peek.firstPosition() + peek.entries().size() - 1;
    if (appendEntryLimiter.tryAcquire(highestPosition)) {
      writeBatch(peek, highestPosition);
    } else {
      appendBackpressureMetrics.deferred();
      LOG.trace(
          "Backpressure happens: in flight {} limit {}",
          appendEntryLimiter.getInflight(),
          appendEntryLimiter.getLimit());
      // we will be called later again
    }
  }

  private void writeBatch(final SequencedBatch peek, final long highestPosition) {
    final var sequencedBatch = sequencer.tryRead();
    // Peeked something, so we should be able to read the same item because we are the only
    // reader. If not, something has gone very wrong.
    assert sequencedBatch == peek;

    final var serialized = serializer.serializeBatch(sequencedBatch);
    final var listener =
        new Listener(
            this,
            highestPosition,
            appenderMetrics.startAppendLatencyTimer(),
            appenderMetrics.startCommitLatencyTimer());
    logStorage.append(sequencedBatch.firstPosition(), highestPosition, serialized, listener);
    actor.submit(this::tryWriteBatch);
  }

  private void onFailure(final Throwable error) {
    LOG.error("Actor {} failed in phase {}.", name, actor.getLifecyclePhase(), error);
    actor.fail(error);
    final var report = HealthReport.unhealthy(this).withIssue(error);
    failureListeners.forEach(l -> l.onFailure(report));
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
