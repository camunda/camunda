/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.session.BrokerLivenessMirror;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;

/**
 * Fences dead brokers — the leader-only liveness sweep, registered as a {@link
 * StreamProcessorLifecycleAware} and self-scheduling on the async task group in {@link
 * #onRecovered}, exactly like the consumer-groups {@code SessionEvictionTask}. On each tick it
 * walks the off-actor {@link BrokerLivenessMirror} (its key set is the bounded work set — only
 * brokers that have registered/heartbeated) and appends a {@code FENCE_BROKER} command for every
 * broker whose session lapsed. The {@code FenceBrokerProcessor} flips it to {@code FENCED}
 * (self-guarded against a racing re-registration), so placement stops targeting it.
 *
 * <p>It emits commands only; the fixed-rate re-run is its own retry. Liveness is leader-local and
 * not replicated, so it is cleared when the node stops leading.
 */
public final class BrokerEvictionTask implements Task, StreamProcessorLifecycleAware {

  private final Duration interval;
  private final Duration sessionTimeout;
  private final BrokerState brokerState;
  private final BrokerLivenessMirror liveness;
  private final InstantSource clock;

  public BrokerEvictionTask(
      final Duration interval,
      final Duration sessionTimeout,
      final BrokerState brokerState,
      final BrokerLivenessMirror liveness,
      final InstantSource clock) {
    this.interval = interval;
    this.sessionTimeout = sessionTimeout;
    this.brokerState = brokerState;
    this.liveness = liveness;
    this.clock = clock;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    context.getScheduleService().runAtFixedRateAsync(interval, this);
  }

  @Override
  public void onClose() {
    liveness.clear();
  }

  @Override
  public void onFailed() {
    liveness.clear();
  }

  @Override
  public void onPaused() {
    liveness.clear();
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var deadline = clock.instant().minus(sessionTimeout);
    for (final var brokerId : liveness.brokerIds()) {
      final var lastSeen = liveness.lastSeen(brokerId);
      if (lastSeen == null || lastSeen.isAfter(deadline)) {
        continue;
      }
      final var broker = brokerState.get(brokerId);
      if (broker == null || broker.status() != BrokerStatus.ACTIVE) {
        continue;
      }
      taskResultBuilder.appendCommandRecord(
          MetadataIntent.FENCE_BROKER,
          new BrokerRecord().setBrokerId(brokerId).setBrokerEpoch(broker.brokerEpoch()));
    }
    return taskResultBuilder.build();
  }
}
