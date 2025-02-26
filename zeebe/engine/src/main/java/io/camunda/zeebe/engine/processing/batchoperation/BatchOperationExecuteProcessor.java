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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.batchoperation.ItemKeys;
import io.camunda.zeebe.engine.state.immutable.BatchOperationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class BatchOperationExecuteProcessor
    implements TypedRecordProcessor<BatchOperationExecutionRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      BatchOperationExecuteProcessor.class);

  private static final int BATCH_SIZE = 10;

  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final int partitionId;

  private final BatchOperationState batchOperationState;

  public BatchOperationExecuteProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior,
      final int partitionId
  ) {
    commandWriter = writers.command();
    stateWriter = writers.state();
    responseWriter = writers.response();
    batchOperationState = processingState.getBatchOperationState();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.partitionId = partitionId;
  }

  @Override
  public void processRecord(final TypedRecord<BatchOperationExecutionRecord> command) {
    final var recordValue = command.getValue();
    LOGGER.debug("Processing new command with key '{}': {}", command.getKey(), recordValue);
    final long batchKey = command.getValue().getBatchOperationKey();
    final int offset = command.getValue().getOffset();

    // TODO do we need more states here? Like EXECUTE, EXECUTING, EXECUTED
    // when do we apply this event?
    final var filteredEntityKeys = fetchFilteredEntityKeys(batchKey);
    final var entityKeys = filteredEntityKeys.subList(offset,
        Math.min(offset + BATCH_SIZE, filteredEntityKeys.size()));

    stateWriter.appendFollowUpEvent(
        command.getKey(), BatchOperationIntent.EXECUTING, command.getValue());

    switch (recordValue.getBatchOperationType()) {
      case PROCESS_CANCELLATION:
        entityKeys.forEach(this::cancelProcessInstance);
    }
    stateWriter.appendFollowUpEvent(
        command.getKey(), BatchOperationIntent.EXECUTED, command.getValue());

    if (hasNextBatch(filteredEntityKeys, offset)) {
      final var followupCommand = new BatchOperationExecutionRecord();
      followupCommand.setBatchOperationKey(batchKey);
      followupCommand.setBatchOperationType(command.getValue().getBatchOperationType());
      followupCommand.setOffset(command.getValue().getOffset() + BATCH_SIZE);
      commandWriter.appendFollowUpCommand(command.getKey(), BatchOperationIntent.EXECUTE,
          followupCommand);
    } else {
      // todo use the BatchOperationRecord
      stateWriter.appendFollowUpEvent(
          command.getKey(), BatchOperationIntent.COMPLETED, command.getValue());
    }
  }

  private List<Long> fetchFilteredEntityKeys(final long batchOperationKey) {
    final var keys = batchOperationState.get(batchOperationKey).map(ItemKeys::getKeys)
        .orElseThrow();
    return keys.stream()
        .filter(key -> Protocol.decodePartitionId(key) == partitionId)
        .toList();
  }

  private boolean hasNextBatch(final List<Long> filteredKeysByPartition, final int offset) {
    return filteredKeysByPartition.size() > offset + BATCH_SIZE;
  }

  private void cancelProcessInstance(final long processInstanceKey) {
    LOGGER.info("Cancelling process instance with key '{}'", processInstanceKey);

    final var command = new ProcessInstanceRecord();
    command.setProcessInstanceKey(processInstanceKey);
    commandWriter.appendFollowUpCommand(processInstanceKey, ProcessInstanceIntent.CANCEL, command);
  }

}
