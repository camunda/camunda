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
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessDeleteDrainState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Aggregates per-partition drain reports on the aggregating partition and re-issues the delete once
 * every partition has drained. A follower forwards its report here; the aggregating partition
 * clears each reporting partition from {@link ProcessDeleteDrainState} and, when the set empties,
 * re-issues the ordinary (now instance-free) resource deletion.
 */
@ExcludeAuthorizationCheck
public final class ProcessDeleteCompleteProcessor
    implements DistributedTypedRecordProcessor<ProcessRecord> {

  private final int partitionId;
  private final StateWriter stateWriter;
  private final ProcessDeleteDrainState processDeleteDrainState;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ProcessDeleteCompleteProcessor(
      final int partitionId,
      final Writers writers,
      final MutableProcessingState processingState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.partitionId = partitionId;
    stateWriter = writers.state();
    processDeleteDrainState = processingState.getProcessDeleteDrainState();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<ProcessRecord> command) {
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {
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
    // always acknowledge, even when the report was a no-op, so the sender stops retrying
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void recordPartitionDrained(final TypedRecord<ProcessRecord> command) {
    final var process = command.getValue();
    final long processDefinitionKey = process.getProcessDefinitionKey();
    final int reportingPartitionId = Protocol.decodePartitionId(command.getKey());

    if (!processDeleteDrainState.hasDrainingPartition(processDefinitionKey, reportingPartitionId)) {
      // already cleared (redelivery) or never expected
      return;
    }

    // keyed with the report's key so the applier can decode the reporting partition to clear it
    stateWriter.appendFollowUpEvent(command.getKey(), ProcessIntent.DELETE_COMPLETED, process);

    if (!processDeleteDrainState.hasDrainingPartition(processDefinitionKey)) {
      startPhysicalDelete(command, process);
    }
  }

  private void startPhysicalDelete(
      final TypedRecord<ProcessRecord> command, final ProcessRecord process) {
    stateWriter.appendFollowUpEvent(command.getKey(), ProcessIntent.FULLY_DELETED, process);

    deleteHistory(command.getKey(), command);
  }

  private void deleteHistory(final long eventKey, final TypedRecord<ProcessRecord> command) {
    // TODO delete history https://github.com/camunda/camunda/issues/56973
  }
}
