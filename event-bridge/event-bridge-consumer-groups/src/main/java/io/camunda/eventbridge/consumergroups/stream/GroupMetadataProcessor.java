/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.stream.StateWriter;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code REBALANCE_GROUP} command: turns it into a {@code GROUP_METADATA_COMMITTED}
 * follow-up event, which {@link GroupMetadataCommittedApplier} applies to the replicated
 * consumer-group metadata. Holds no state.
 */
public final class GroupMetadataProcessor implements TypedRecordProcessor<GroupMetadataRecord> {

  @Override
  public void processRecord(
      final TypedRecord<GroupMetadataRecord> command, final StateWriter stateWriter) {
    final var cmd = command.getValue();
    final var event =
        new GroupMetadataRecord().setGroupId(cmd.getGroupId()).setPayload(cmd.getPayload());
    stateWriter.appendFollowUpEvent(
        command.getKey(), CoordinatorIntent.GROUP_METADATA_COMMITTED, event);
  }
}
