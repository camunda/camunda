/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.FollowUpEventMetadata;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local command processor which sends the FAIL_PARTITION command to the lead partition. */
@ExcludeAuthorizationCheck
public final class BatchOperationPartitionLifecycleFailProcessor
    implements TypedRecordProcessor<BatchOperationPartitionLifecycleRecord> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationPartitionLifecycleFailProcessor.class);

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final KeyGenerator keyGenerator;
  private final BatchOperationMetrics metrics;
  private final BatchOperationState batchOperationState;

  public BatchOperationPartitionLifecycleFailProcessor(
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final KeyGenerator keyGenerator,
      final BatchOperationMetrics metrics,
      final ProcessingState processingState) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.keyGenerator = keyGenerator;
    this.metrics = metrics;
    batchOperationState = processingState.getBatchOperationState();
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationPartitionLifecycleRecord> command) {
    final var recordValue = command.getValue();
    final long batchOperationKey = recordValue.getBatchOperationKey();
    LOGGER.debug(
        "Marking batch operation {} as failed on partition {}",
        command.getValue().getBatchOperationKey(),
        command.getPartitionId());

    final int boLeadPartitionId = Protocol.decodePartitionId(batchOperationKey);
    final var batchInternalFail =
        new BatchOperationPartitionLifecycleRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSourcePartitionId(command.getPartitionId())
            .setError(recordValue.getError());

    LOGGER.debug(
        "Send internal fail command for batch operation {} to original partition {}",
        batchOperationKey,
        boLeadPartitionId);

    if (boLeadPartitionId == command.getPartitionId()) {
      commandWriter.appendFollowUpCommand(
          batchOperationKey,
          BatchOperationIntent.FAIL_PARTITION,
          batchInternalFail,
          FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));

      final var oBatchOperation = batchOperationState.get(batchOperationKey);
      oBatchOperation.ifPresent(
          persistedBatchOperation ->
              metrics.recordFailed(persistedBatchOperation.getBatchOperationType()));
    } else {
      stateWriter.appendFollowUpEvent(
          batchOperationKey,
          BatchOperationIntent.PARTITION_FAILED,
          batchInternalFail,
          FollowUpEventMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
      commandDistributionBehavior
          .withKey(keyGenerator.nextKey())
          .inQueue(DistributionQueue.BATCH_OPERATION)
          .forPartition(boLeadPartitionId)
          .distribute(
              ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
              BatchOperationIntent.FAIL_PARTITION,
              batchInternalFail,
              command.getAuthInfo());
    }
  }
}
