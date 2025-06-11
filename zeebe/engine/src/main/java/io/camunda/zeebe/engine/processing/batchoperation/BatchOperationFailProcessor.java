/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationFailProcessor
    implements TypedRecordProcessor<BatchOperationCreationRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationFailProcessor.class);

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final KeyGenerator keyGenerator;
  private final int partitionId;

  public BatchOperationFailProcessor(
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final KeyGenerator keyGenerator,
      final int partitionId) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.keyGenerator = keyGenerator;
    this.partitionId = partitionId;
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationCreationRecord> command) {
    final var recordValue = command.getValue();
    LOGGER.debug("Processing new command with key '{}': {}", command.getKey(), recordValue);

    final int originPartitionId = Protocol.decodePartitionId(recordValue.getBatchOperationKey());
    final var batchInternalFail =
        new BatchOperationPartitionLifecycleRecord()
            .setBatchOperationKey(recordValue.getBatchOperationKey())
            .setSourcePartitionId(partitionId);

    LOGGER.debug(
        "Send internal fail command for batch operation {} to original partition {}",
        recordValue.getBatchOperationKey(),
        originPartitionId);

    if (originPartitionId == partitionId) {
      commandWriter.appendFollowUpCommand(
          recordValue.getBatchOperationKey(),
          BatchOperationIntent.FAIL_PARTITION,
          batchInternalFail);
    } else {
      stateWriter.appendFollowUpEvent(
          recordValue.getBatchOperationKey(),
          BatchOperationIntent.PARTITION_FAILED,
          batchInternalFail);
      commandDistributionBehavior
          .withKey(keyGenerator.nextKey())
          .inQueue(DistributionQueue.BATCH_OPERATION)
          .forPartition(originPartitionId)
          .distribute(
              ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
              BatchOperationIntent.FAIL_PARTITION,
              batchInternalFail);
    }
  }
}
