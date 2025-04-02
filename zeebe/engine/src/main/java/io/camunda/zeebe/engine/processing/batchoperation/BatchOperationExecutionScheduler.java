/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {

  public static final int CHUNK_SIZE_IN_RECORD = 10;

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);
  private static final int QUERY_SIZE = 10000;
  private final Duration pollingInterval;

  private final BatchOperationState batchOperationState;
  private ReadonlyStreamProcessorContext processingContext;
  private final SearchClientsProxy queryService;

  /** Marks if this scheduler is currently executing or not. */
  private final AtomicBoolean executing = new AtomicBoolean(false);

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final SearchClientsProxy searchClientsProxy,
      final Duration pollingInterval) {
    batchOperationState = scheduledTaskStateFactory.get().getBatchOperationState();
    queryService = searchClientsProxy;
    this.pollingInterval = pollingInterval;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    processingContext = context;
    scheduleExecution();
  }

  @Override
  public void onResumed() {
    scheduleExecution();
  }

  private void scheduleExecution() {
    if (!executing.get()) {
      processingContext
          .getScheduleService()
          .runDelayedAsync(pollingInterval, this::execute, AsyncTaskGroup.BATCH_OPERATIONS);
    } else {
      LOG.warn("Execution is already in progress, skipping scheduling.");
    }
  }

  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    try {
      LOG.trace("Looking for pending batch operations to execute (scheduled).");
      executing.set(true);
      batchOperationState.foreachPendingBatchOperation(
          bo -> executeBatchOperation(bo, taskResultBuilder));
      return taskResultBuilder.build();
    } finally {
      executing.set(false);
      scheduleExecution();
    }
  }

  private void executeBatchOperation(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {
    final var keys = queryAllKeys(batchOperation);
    for (int i = 0; i < keys.size(); i += CHUNK_SIZE_IN_RECORD) {
      final Set<Long> chunkKeys =
          keys.stream().skip(i).limit(CHUNK_SIZE_IN_RECORD).collect(Collectors.toSet());
      appendChunk(batchOperation.getKey(), taskResultBuilder, chunkKeys);
    }

    appendExecution(batchOperation.getKey(), taskResultBuilder);
  }

  private void appendChunk(
      final Long batchOperationKey,
      final TaskResultBuilder taskResultBuilder,
      final Set<Long> keys) {
    final var command = new BatchOperationChunkRecord();
    command.setBatchOperationKey(batchOperationKey);
    command.setItemKeys(keys);

    LOG.debug(
        "Appending batch operation {} subbatch with {} items.", batchOperationKey, keys.size());
    taskResultBuilder.appendCommandRecord(
        batchOperationKey, BatchOperationChunkIntent.CREATE, command);
  }

  private void appendExecution(
      final Long batchOperationKey, final TaskResultBuilder taskResultBuilder) {
    final var command = new BatchOperationExecutionRecord();
    command.setBatchOperationKey(batchOperationKey);

    LOG.debug("Appending batch operation execution {}", batchOperationKey);
    taskResultBuilder.appendCommandRecord(
        batchOperationKey, BatchOperationExecutionIntent.EXECUTE, command, batchOperationKey);
  }

  private Set<Long> queryAllKeys(final PersistedBatchOperation batchOperation) {
    return switch (batchOperation.getBatchOperationType()) {
      case PROCESS_CANCELLATION -> queryAllProcessInstanceKeys(batchOperation);
      default ->
          throw new IllegalArgumentException(
              "Unexpected batch operation type: " + batchOperation.getBatchOperationType());
    };
  }

  private Set<Long> queryAllProcessInstanceKeys(final PersistedBatchOperation batchOperation) {
    final ProcessInstanceFilter filter =
        batchOperation.getEntityFilter(ProcessInstanceFilter.class);

    final var itemKeys = new LinkedHashSet<Long>();

    Object[] searchValues = null;
    while (true) {

      final var page =
          SearchQueryPageBuilders.page().size(QUERY_SIZE).searchAfter(searchValues).build();
      final var query =
          SearchQueryBuilders.processInstanceSearchQuery()
              .filter(filter)
              .page(page)
              .resultConfig(c -> c.onlyKey(true))
              .build();
      final var result = queryService.searchProcessInstances(query);
      itemKeys.addAll(
          result.items().stream()
              .map(ProcessInstanceEntity::processInstanceKey)
              .collect(Collectors.toSet()));
      searchValues = result.lastSortValues();

      if (itemKeys.size() >= result.total() || result.items().isEmpty()) {
        break;
      }
    }

    return itemKeys;
  }
}
