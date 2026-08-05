/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.historydeletion.HistoryDeletionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Aggregates per-partition drain reports on the deployment partition. Each report clears the
 * reporting partition ({@link ProcessIntent#DELETE_COMPLETED}); once none remain, the definition is
 * marked gone cluster-wide ({@link ProcessIntent#FULLY_DELETED}). Physical removal already happened
 * locally on each partition (see {@code BpmnProcessDeletionBehavior}).
 */
@ExcludeAuthorizationCheck
public final class ProcessDeleteCompleteProcessor
    implements DistributedTypedRecordProcessor<ProcessRecord> {

  private final int currentPartitionId;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ProcessState processState;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ProcessDeleteCompleteProcessor(
      final int currentPartitionId,
      final Writers writers,
      final MutableProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.currentPartitionId = currentPartitionId;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    processState = processingState.getProcessState();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<ProcessRecord> command) {
    if (currentPartitionId == Protocol.DEPLOYMENT_PARTITION) {
      recordPartitionDrained(command);
    } else {
      commandDistributionBehavior
          .withKey(command.getKey())
          .unordered()
          .forPartition(Protocol.DEPLOYMENT_PARTITION)
          .distribute(command);
    }
  }

  @Override
  public void processDistributedCommand(final TypedRecord<ProcessRecord> command) {
    recordPartitionDrained(command);
    // always acknowledge, even when the report was rejected, so the sender stops retrying
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void recordPartitionDrained(final TypedRecord<ProcessRecord> command) {
    final var process = command.getValue();
    final long processDefinitionKey = process.getProcessDefinitionKey();
    final int reportingPartitionId = Protocol.decodePartitionId(command.getKey());

    if (!processState.hasPendingDeletion(processDefinitionKey, reportingPartitionId)) {
      // already cleared (redelivery) or never expected
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          "Expected to complete draining for process definition '%d' reported by partition '%d', but no outstanding drain report exists"
              .formatted(processDefinitionKey, reportingPartitionId));
      return;
    }

    // keyed with the report's key so the applier can decode the reporting partition to clear it
    stateWriter.appendFollowUpEvent(command.getKey(), ProcessIntent.DELETE_COMPLETED, process);

    // emit FULLY_DELETED exactly once: only when this report cleared the last outstanding partition
    if (!processState.hasPendingDeletion(processDefinitionKey)) {
      finishProcessDelete(command, process);
    }
  }

  private void finishProcessDelete(
      final TypedRecord<ProcessRecord> command, final ProcessRecord process) {
    stateWriter.appendFollowUpEvent(command.getKey(), ProcessIntent.FULLY_DELETED, process);
  }
}
