/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import io.camunda.zeebe.engine.state.immutable.HistoryDeletionState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryDeletionScheduler implements StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(HistoryDeletionScheduler.class);
  private ReadonlyStreamProcessorContext processingContext;
  private final AtomicBoolean executing = new AtomicBoolean(false);
  private final Duration interval = Duration.ofSeconds(1);
  private final HistoryDeletionState historyDeletionState;

  public HistoryDeletionScheduler(final Supplier<ScheduledTaskState> scheduledTaskStateFactory) {
    historyDeletionState = scheduledTaskStateFactory.get().getHistoryDeletionState();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    processingContext = context;
    scheduleExecution(interval);
  }

  @Override
  public void onResumed() {
    scheduleExecution(interval);
  }

  private void scheduleExecution(final Duration nextDelay) {
    if (!executing.get()) {
      processingContext
          .getScheduleService()
          .runDelayedAsync(nextDelay, this::execute, AsyncTaskGroup.HISTORY_DELETION);
    } else {
      LOG.warn("Execution is already in progress, skipping scheduling.");
    }
  }

  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    try {
      LOG.debug("Looking for the next instance to delete.");
      executing.set(true);

      // Find next process instance to delete (return true to break out of loop)
      historyDeletionState.forEachProcessInstanceToDelete(
          processInstanceKey -> {
            LOG.debug("Deleting process instance with key {}", processInstanceKey.getValue());
            return false;
          });

      // Delete from ES

      // If delete = success
      //  Write command to delete process instance key from primary deletion CF
    } finally {
      executing.set(false);
      scheduleExecution(interval);
    }
    return taskResultBuilder.build();
  }
}
