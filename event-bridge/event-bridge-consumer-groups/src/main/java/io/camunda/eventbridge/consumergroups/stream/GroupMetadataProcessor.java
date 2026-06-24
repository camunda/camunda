/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.stream.StreamRecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Replicates consumer-group metadata on the coordinator partition. Following the engine's
 * command-processor / event-applier split, it does <em>not</em> mutate state directly: on a
 * rebalance command it appends a {@code GROUP_METADATA_COMMITTED} follow-up event via the {@link
 * io.camunda.eventbridge.stream.StateWriter}, which both writes the event and applies it through
 * the registered {@link GroupMetadataCommittedApplier}. The same applier runs on replay, so every
 * replica rebuilds identical membership and a new leader can restore the registry after failover.
 */
public final class GroupMetadataProcessor extends StreamRecordProcessor {

  public GroupMetadataProcessor(final DbGroupMetadataState groupMetadataState) {
    super(EventBridgeRecordValues.GROUP_METADATA_VALUE_TYPE);
    appliers()
        .register(
            CoordinatorIntent.GROUP_METADATA_COMMITTED,
            new GroupMetadataCommittedApplier(groupMetadataState));
  }

  @Override
  protected void processCommand(final TypedRecord command) {
    final var cmd = (GroupMetadataRecord) command.getValue();

    final var event =
        new GroupMetadataRecord().setGroupId(cmd.getGroupId()).setPayload(cmd.getPayload());

    stateWriter()
        .appendFollowUpEvent(command.getKey(), CoordinatorIntent.GROUP_METADATA_COMMITTED, event);
  }
}
