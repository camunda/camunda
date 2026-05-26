/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;

/**
 * Periodically checks whether the cross-partition message-start dedup state on {@code P_B} holds
 * any tombstone entry whose deletion deadline has passed and, if so, writes a single {@link
 * MessageStartProcessInstanceRequestIntent#SWEEP_TOMBSTONES} trigger command. The actual deletion
 * work (scanning state and writing one {@link
 * MessageStartProcessInstanceRequestIntent#TOMBSTONE_DELETED} event per past-deadline entry) is
 * done by {@link MessageStartDedupTombstoneSweepProcessor}.
 *
 * <p>The trigger-then-batch split mirrors {@link MessageTimeToLiveCheckScheduler} / {@link
 * MessageBatchExpireProcessor}: the scheduler runs on the leader only, makes a cheap read-only
 * probe, and produces a single command that the regular stream-processor pipeline turns into the
 * deterministic state-change events.
 */
public final class MessageStartDedupTombstoneSweepScheduler
    implements Task, StreamProcessorLifecycleAware {

  private final Duration executionInterval;
  private final MessageStartProcessInstanceDedupState dedupState;

  private ProcessingScheduleService scheduleService;
  private InstantSource clock;

  public MessageStartDedupTombstoneSweepScheduler(
      final Duration executionInterval, final MessageStartProcessInstanceDedupState dedupState) {
    this.executionInterval = executionInterval;
    this.dedupState = dedupState;
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    if (dedupState.hasTombstonePastDeadline(clock.millis())) {
      taskResultBuilder.appendCommandRecord(
          MessageStartProcessInstanceRequestIntent.SWEEP_TOMBSTONES,
          new MessageStartProcessInstanceRequestRecord());
    }
    reschedule(executionInterval);
    return taskResultBuilder.build();
  }

  private void reschedule(final Duration idleInterval) {
    scheduleService.runAt(clock.millis() + idleInterval.toMillis(), this);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    scheduleService = context.getScheduleService();
    clock = context.getClock();
    reschedule(executionInterval);
  }
}
