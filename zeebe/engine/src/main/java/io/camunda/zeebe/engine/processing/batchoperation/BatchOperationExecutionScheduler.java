/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import static io.camunda.search.query.SearchQueryBuilders.processInstanceSearchQuery;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

  public BatchOperationExecutionScheduler(
      final ProcessingState processingState,
      final Set<SearchQueryService> queryServices,
      final KeyGenerator keyGenerator,
      final Duration pollingInterval) {
    batchOperationState = processingState.getBatchOperationState();
    this.keyGenerator = keyGenerator;
    this.queryServices = queryServices;
    this.pollingInterval = pollingInterval;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    processingContext = context;
    processingContext.getScheduleService().runAtFixedRateAsync(pollingInterval, this::execute);
  }

  @Override
  public void onResumed() {
    processingContext.getScheduleService().runAtFixedRateAsync(pollingInterval, this::execute);
  }

  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    LOG.debug("Executing batch operation");
    batchOperationState.foreachPendingBatchOperation(
        bo -> executeBatchOperation(bo, taskResultBuilder));
    return taskResultBuilder.build();
  }

  private void executeBatchOperation(
      final PersistedBatchOperation batchOperation, final TaskResultBuilder taskResultBuilder) {
    final var keys = queryKeys(batchOperation);

    final var key = keyGenerator.nextKey();
    final var command = new BatchOperationExecutionRecord();
    command.setBatchOperationKey(batchOperation.getKey());
    command.setBatchOperationType(batchOperation.getBatchOperationType());
    command.setKeys(keys);
    taskResultBuilder.appendCommandRecord(key, BatchOperationIntent.EXECUTE, command);

    LOG.debug("Executing batch operation with key {}", key);
  }

  private Set<Long> queryKeys(final PersistedBatchOperation batchOperation) {
    return switch (batchOperation.getBatchOperationType()) {
      case PROCESS_CANCELLATION -> queryProcessInstanceKeys(batchOperation);
      default -> throw new IllegalArgumentException(
          "Unexpected batch operation type: " + batchOperation.getBatchOperationType());
    };
  }

  private Set<Long> queryProcessInstanceKeys(final PersistedBatchOperation batchOperation) {
    final var filter = batchOperation.getFilter(ProcessInstanceFilter.class).toBuilder()
        .partitionIds(List.of(processingContext.getPartitionId()))
        .build();
    final var query =
        processInstanceSearchQuery(
            q ->
                q.filter(filter)
                    .page(p -> p.from(0).size(BATCH_SIZE))
                    .resultConfig(r -> r.onlyKey(true)));
    final ProcessInstanceServices processInstanceServices =
        getQueryService(ProcessInstanceServices.class);
    return processInstanceServices.search(query).items().stream()
        .map(ProcessInstanceEntity::processInstanceKey)
        .collect(Collectors.toSet());
  }

  private <T extends SearchQueryService> T getQueryService(final Class<T> queryServicesClass) {
    return queryServices.stream()
        .filter(queryServicesClass::isInstance)
        .map(queryServicesClass::cast)
        .findFirst()
        .orElseThrow();
  }
}
