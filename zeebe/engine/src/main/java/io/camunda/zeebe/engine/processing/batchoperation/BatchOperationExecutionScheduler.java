/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.search.query.SearchQueryBuilders.processInstanceSearchQuery;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationExecutionScheduler implements StreamProcessorLifecycleAware {
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationExecutionScheduler.class);
  private static final int BATCH_SIZE = 10;
  private final Duration pollingInterval;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  private final BatchOperationState batchOperationState;
  private ReadonlyStreamProcessorContext processingContext;
  private final ProcessInstanceServices processInstanceServices;

  public BatchOperationExecutionScheduler(
      final ProcessingState processingState,
      final ProcessInstanceServices processInstanceServices,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final Duration pollingInterval) {
    batchOperationState = processingState.getBatchOperationState();
    commandWriter = writers.command();
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    this.processInstanceServices = processInstanceServices;
    this.pollingInterval = pollingInterval;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    processingContext = context;
    processingContext.getScheduleService().runAtFixedRate(pollingInterval, this::execute);
  }

  @Override
  public void onResumed() {
    processingContext.getScheduleService().runAtFixedRate(pollingInterval, this::execute);
  }

  private void execute() {
    LOG.debug("Executing batch operation");
    batchOperationState.foreachPendingBatchOperation(this::executeBatchOperation);
  }

  private void executeBatchOperation(final PersistedBatchOperation batchOperation) {
    final var keys = queryKeys(batchOperation);

    final var key = keyGenerator.nextKey();
    final var command = new BatchOperationExecutionRecord();
    command.setKeys(keys);
    commandWriter.appendFollowUpCommand(key, BatchOperationIntent.EXECUTE, command);

    // FIXME do we need separate intents for creation and execution records?
    stateWriter.appendFollowUpEvent(
        batchOperation.getKey(),
        BatchOperationIntent.EXECUTING,
        new BatchOperationCreationRecord()
            .setBatchOperationType(batchOperation.getBatchOperationType())
            .setFilter(batchOperation.getFilterBuffer()));

    LOG.debug("Executing batch operation with key {}", key);
  }

  private Set<Long> queryKeys(final PersistedBatchOperation batchOperation) {
    return switch (batchOperation.getBatchOperationType()) {
      case PROCESS_CANCELLATION -> queryProcessInstanceKeys(batchOperation);
      default ->
          throw new IllegalArgumentException(
              "Unexpected batch operation type: " + batchOperation.getBatchOperationType());
    };
  }

  private Set<Long> queryProcessInstanceKeys(final PersistedBatchOperation batchOperation) {
    final var filter = batchOperation.getFilter(ProcessInstanceFilter.class);
    final var partitionId = processingContext.getPartitionId();
    // TODO add partition id to the filter
    final var query =
        processInstanceSearchQuery(
            q ->
                q.filter(filter)
                    .page(p -> p.from(0).size(BATCH_SIZE))
                    .resultConfig(r -> r.onlyKey(true)));
    return processInstanceServices.search(query).items().stream()
        .map(ProcessInstanceEntity::processInstanceKey)
        .collect(Collectors.toSet());
  }
}
