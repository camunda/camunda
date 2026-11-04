/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.engine.state.immutable.PendingSubscriptionRecovery.NO_ELEMENT_INSTANCE;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingSubscriptionRecovery;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Rebuilds the transient pending (process) message subscriptions after recovery, in bounded chunks
 * on the async actor, so that processing doesn't wait for a scan over all subscriptions. The
 * pending state is only read by the checkers, which tolerate it being incomplete for a while: a
 * subscription that isn't recovered yet is simply retried later. The tasks stop with the stream
 * processor, since the schedule service skips them once it closes.
 */
public final class PendingSubscriptionRecoveryScheduler implements StreamProcessorLifecycleAware {

  static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private final PendingProcessMessageSubscriptionState processSubscriptionState;
  private final PendingMessageSubscriptionState messageSubscriptionState;
  private final int batchLimit;

  private ProcessingScheduleService scheduleService;

  public PendingSubscriptionRecoveryScheduler(
      final PendingProcessMessageSubscriptionState processSubscriptionState,
      final PendingMessageSubscriptionState messageSubscriptionState,
      final int batchLimit) {
    this.processSubscriptionState = processSubscriptionState;
    this.messageSubscriptionState = messageSubscriptionState;
    this.batchLimit = batchLimit;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    scheduleService = context.getScheduleService();
    scheduleService.runDelayedAsync(
        Duration.ZERO, new RecoveryTask("process message subscriptions", processSubscriptionState));
    scheduleService.runDelayedAsync(
        Duration.ZERO, new RecoveryTask("message subscriptions", messageSubscriptionState));
  }

  private final class RecoveryTask implements Task {

    private final String name;
    private final PendingSubscriptionRecovery recovery;
    private final long startedAtNanos = System.nanoTime();
    private long nextElementInstanceKey = NO_ELEMENT_INSTANCE;
    private int chunks;

    private RecoveryTask(final String name, final PendingSubscriptionRecovery recovery) {
      this.name = name;
      this.recovery = recovery;
    }

    @Override
    public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
      try {
        nextElementInstanceKey = recovery.recoverPending(nextElementInstanceKey, batchLimit);
      } catch (final RuntimeException e) {
        // nothing else rebuilds the pending subscriptions in this leader term
        LOG.warn("Failed to recover a chunk of pending {}, retrying in {}", name, RETRY_DELAY, e);
        scheduleService.runDelayedAsync(RETRY_DELAY, this);
        return taskResultBuilder.build();
      }
      chunks++;

      if (nextElementInstanceKey == NO_ELEMENT_INSTANCE) {
        LOG.info(
            "Recovered pending {} in {} ms ({} chunks)",
            name,
            Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis(),
            chunks);
      } else {
        scheduleService.runDelayedAsync(Duration.ZERO, this);
      }
      return taskResultBuilder.build();
    }
  }
}
