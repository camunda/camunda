/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import io.camunda.search.clients.WriteClientsProxy;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.immutable.HistoryDeletionState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryDeletionScheduler implements StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(HistoryDeletionScheduler.class);
  private ReadonlyStreamProcessorContext processingContext;
  private final AtomicBoolean executing = new AtomicBoolean(false);
  private final Duration interval;
  private final int batchSize;
  private final HistoryDeletionState historyDeletionState;
  private final WriteClientsProxy writeClientsProxy;

  public HistoryDeletionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final WriteClientsProxy writeClientsProxy,
      final EngineConfiguration config) {
    historyDeletionState = scheduledTaskStateFactory.get().getHistoryDeletionState();
    this.writeClientsProxy = writeClientsProxy;
    interval = config.getHistoryDeletionInterval();
    batchSize = config.getHistoryDeletionBatchSize();
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
      final var currentBatchSize = new AtomicInteger(0);

      // Find next process instance to delete (return true to break out of loop)
      historyDeletionState.forEachProcessInstanceToDelete(
          (processInstanceKey, batchOperationReference) -> {
            LOG.debug("Deleting process instance with key {}", processInstanceKey);
            writeClientsProxy.deleteHistoricData(processInstanceKey);

            // TODO only delete if delete = success
            taskResultBuilder.appendCommandRecord(
                processInstanceKey,
                ProcessInstanceIntent.DELETE_COMPLETE,
                new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey),
                FollowUpCommandMetadata.of(
                    metadata -> metadata.batchOperationReference(batchOperationReference)));

            return currentBatchSize.incrementAndGet() < batchSize;
          });

    } finally {
      executing.set(false);
      scheduleExecution(interval);
    }
    return taskResultBuilder.build();
  }
}
