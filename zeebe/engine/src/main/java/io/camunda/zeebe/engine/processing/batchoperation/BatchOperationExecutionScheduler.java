/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import com.google.common.collect.Lists;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.zeebe.engine.secondarydb.SecondaryDbQueryService;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationChunkIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.AsyncTaskGroup;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);
  private static final int BATCH_SIZE = 10;
  private final Duration pollingInterval;
  private final KeyGenerator keyGenerator;

  private final BatchOperationState batchOperationState;
  private ReadonlyStreamProcessorContext processingContext;
  private final SecondaryDbQueryService queryService;

  // todo this does not cover a full pod failure
  private final Set<Long> batchOperationKeysProcessed = new HashSet<>();

  public BatchOperationExecutionScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final SecondaryDbQueryService queryService,
      final KeyGenerator keyGenerator,
      final Duration pollingInterval) {
    batchOperationState = scheduledTaskStateFactory.get().getBatchOperationState();
    this.keyGenerator = keyGenerator;
    this.queryService = queryService;
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
    processingContext
        .getScheduleService()
        .runDelayedAsync(pollingInterval, this::execute, AsyncTaskGroup.BATCH_OPERATIONS);
  }

  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    try {
      LOG.trace("Looking for pending batch operations to execute (scheduled)");
      final var boKeys = new HashSet<Long>();
      batchOperationState.foreachPendingBatchOperation(
          bo -> {
            if (batchOperationKeysProcessed.contains(bo.getKey())) {
              return;
            }

            executeBatchOperation(bo, taskResultBuilder);
            boKeys.add(bo.getKey());
          });
      // todo there is a small gap when the command append fails
      batchOperationKeysProcessed.addAll(boKeys);
      return taskResultBuilder.build();
    } finally {
      scheduleExecution();
    }
  }

  private void executeBatchOperation(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {
    final var keys = queryAllKeys(batchOperation);

    Lists.partition(new ArrayList<>(keys), BATCH_SIZE)
        .forEach(partition -> appendChunk(batchOperation, taskResultBuilder, partition));

    // todo needed later
    //    appendExecution(batchOperation, taskResultBuilder);
  }

  private void appendChunk(
      final PersistedBatchOperation batchOperation,
      final TaskResultBuilder taskResultBuilder,
      final List<Long> keys) {
    final var chunkKey = keyGenerator.nextKey();
    final var command = new BatchOperationChunkRecord();
    command.setBatchOperationKey(batchOperation.getKey());
    command.setEntityKeys(keys);
    command.setChunkKey(chunkKey);

    LOG.debug(
        "Appending batch operation {} subbatch with key {}", batchOperation.getKey(), chunkKey);
    taskResultBuilder.appendCommandRecord(chunkKey, BatchOperationChunkIntent.CREATE, command);
  }

  //  TODO needed later
  /*  private void appendExecution(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {
    final var command = new BatchOperationExecutionRecord();
    command.setBatchOperationKey(batchOperation.getKey());

    LOG.debug("Appending batch operation execution {}", batchOperation.getKey());
    taskResultBuilder.appendCommandRecord(
        batchOperation.getKey(), BatchOperationIntent.EXECUTE, command, batchOperation.getKey());
  }*/

  private List<Long> queryAllKeys(final PersistedBatchOperation batchOperation) {
    return switch (batchOperation.getBatchOperationType()) {
      case PROCESS_CANCELLATION -> queryAllProcessInstanceKeys(batchOperation);
      default ->
          throw new IllegalArgumentException(
              "Unexpected batch operation type: " + batchOperation.getBatchOperationType());
    };
  }

  private List<Long> queryAllProcessInstanceKeys(final PersistedBatchOperation batchOperation) {
    final ProcessInstanceFilter filter =
        batchOperation.getEntityFilter(ProcessInstanceFilter.class);

    final var itemKeys = new ArrayList<Long>();

    Object[] searchValues = null;
    final int batchSize = 400000;
    while (true) {

      final var page =
          SearchQueryPageBuilders.page().size(batchSize).searchAfter(searchValues).build();

      final var result = queryService.queryProcessInstanceKeys(filter, page);
      itemKeys.addAll(result.items());
      searchValues = result.lastSortValues();

      if (itemKeys.size() >= result.total() || result.items().isEmpty()) {
        break;
      }
    }

    return itemKeys;
  }
}
