/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.relocation;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.RelocationState;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class ScaleRelocationOnPartitionCompleteProcessor
    implements TypedRecordProcessor<ScaleRecord> {

  private final int partitionId;
  private final StateWriter stateWriter;
  private final RelocationState relocationState;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public ScaleRelocationOnPartitionCompleteProcessor(
      final int partitionId,
      final Writers writers,
      final RelocationState relocationState,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.partitionId = partitionId;
    stateWriter = writers.state();
    this.relocationState = relocationState;
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ScaleRecord> record) {
    stateWriter.appendFollowUpEvent(
        record.getKey(), ScaleIntent.RELOCATION_ON_PARTITION_COMPLETED, record.getValue());

    if (partitionId == 1) {
      // TODO: Update state to track relocation progress
    }
    // TODO: local routing info should be merged with the routing info from the received record to
    // handle the case where this partition has not received "RELOCATION_START" command yet.
    final var routingInfo = relocationState.getRoutingInfo();
    if (routingInfo.completedPartitions().size() == routingInfo.newPartitionCount()) {
      stateWriter.appendFollowUpEvent(
          record.getKey(), ScaleIntent.RELOCATION_COMPLETED, record.getValue());
    }

    commandDistributionBehavior.acknowledgeCommand(record);
  }
}
