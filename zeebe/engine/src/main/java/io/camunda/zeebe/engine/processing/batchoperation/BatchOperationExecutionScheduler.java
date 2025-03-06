/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import com.google.common.collect.Lists;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPageBuilders;
import static io.camunda.search.query.SearchQueryBuilders.processInstanceSearchQuery;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationSubbatchRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);
  private static final int BATCH_SIZE = 10;
  private final Duration pollingInterval;
  private final KeyGenerator keyGenerator;

  private final BatchOperationState batchOperationState;
  private ReadonlyStreamProcessorContext processingContext;
  private final Set<SearchQueryService> queryServices;
  private final ProcessInstanceServices processInstanceServices;

  // todo this does not cover a full pod failure
  private final Set<Long> batchOperationKeysProcessed = new HashSet<>();

  public BatchOperationExecutionScheduler(
      final ProcessingState processingState,
      final Set<SearchQueryService> queryServices,
      final KeyGenerator keyGenerator,
      final Duration pollingInterval) {
    batchOperationState = processingState.getBatchOperationState();
    this.keyGenerator = keyGenerator;
    this.queryServices = queryServices;
    this.pollingInterval = pollingInterval;
    processInstanceServices = getQueryService(ProcessInstanceServices.class);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    processingContext = context;
    processingContext.getScheduleService().runDelayedAsync(pollingInterval, this::execute);
  }

  @Override
  public void onResumed() {
    processingContext.getScheduleService().runDelayedAsync(pollingInterval, this::execute);
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
      processingContext.getScheduleService().runDelayedAsync(pollingInterval, this::execute);
    }
  }

  private void executeBatchOperation(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {
    final var keys = queryAllKeys(batchOperation);

    Lists.partition(new ArrayList<>(keys), BATCH_SIZE)
        .forEach(
            partition -> appendSubbatch(batchOperation, taskResultBuilder, partition));

    appendExecution(batchOperation, taskResultBuilder);
  }

  private void appendSubbatch(final PersistedBatchOperation batchOperation,
      final TaskResultBuilder taskResultBuilder, final List<Long> keys) {
    final var subbatchKey = keyGenerator.nextKey();
    final var command = new BatchOperationSubbatchRecord();
    command.setBatchOperationKey(batchOperation.getKey());
    command.setKeys(keys);
    command.setSubbatchKey(subbatchKey);

    LOG.debug("Appending batch operation {} subbatch with key {}", batchOperation.getKey(),
        subbatchKey);
    taskResultBuilder.appendCommandRecord(subbatchKey,
        BatchOperationIntent.CREATE_SUBBATCH,
        command, batchOperation.getKey());
  }

  private void appendExecution(final PersistedBatchOperation batchOperation,
      final TaskResultBuilder taskResultBuilder) {
    final var command = new BatchOperationExecutionRecord();
    command.setBatchOperationKey(batchOperation.getKey());
    command.setBatchOperationType(batchOperation.getBatchOperationType());
    command.setOffset(0);

    LOG.debug("Appending batch operation execution {}", batchOperation.getKey());
    taskResultBuilder.appendCommandRecord(batchOperation.getKey(), BatchOperationIntent.EXECUTE,
        command, batchOperation.getKey());
  }

  private Set<Long> queryAllKeys(final PersistedBatchOperation batchOperation) {
    return switch (batchOperation.getBatchOperationType()) {
      case PROCESS_CANCELLATION -> queryAllProcessInstanceKeys(batchOperation);
      default -> throw new IllegalArgumentException(
          "Unexpected batch operation type: " + batchOperation.getBatchOperationType());
    };
  }

  private Set<Long> queryAllProcessInstanceKeys(final PersistedBatchOperation batchOperation) {
    final var filter = batchOperation.getFilter(ProcessInstanceFilter.class).toBuilder()
        .partitionIds(List.of(processingContext.getPartitionId()))
        .build();

    final var itemKeys = new HashSet<Long>();

    Object[] searchValues = null;
    final int batchSize = 400000;
    while (true) {

      final var page = SearchQueryPageBuilders.page()
          .size(batchSize)
          .searchAfter(searchValues)
          .build();
      final var query =
          processInstanceSearchQuery(
              q ->
                  q.filter(filter)
                      .page(page)
                      .resultConfig(r -> r.onlyKey(true)));

      final var result = processInstanceServices.search(query);
      itemKeys.addAll(
          result.items().stream().map(ProcessInstanceEntity::processInstanceKey).toList());
      searchValues = result.lastSortValues();

      if (itemKeys.size() >= result.total() || result.items().isEmpty()) {
        break;
      }
    }

    return itemKeys;
  }

  private <T extends SearchQueryService> T getQueryService(final Class<T> queryServicesClass) {
    return queryServices.stream()
        .filter(queryServicesClass::isInstance)
        .map(queryServicesClass::cast)
        .findFirst()
        .orElseThrow();
  }
}
