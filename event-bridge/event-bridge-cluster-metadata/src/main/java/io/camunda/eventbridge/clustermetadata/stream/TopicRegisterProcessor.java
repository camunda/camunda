/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.stream.StateWriter;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code REGISTER_TOPIC} command: turns it into a {@code TOPIC_REGISTERED} follow-up
 * event, which {@link TopicRegisteredApplier} applies to the replicated registry. Holds no state.
 */
final class TopicRegisterProcessor implements TypedRecordProcessor<TopicRecord> {

  private final StateWriter stateWriter;

  TopicRegisterProcessor(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();
    final var event =
        new TopicRecord()
            .setName(cmd.getName())
            .setOp(cmd.getOp())
            .setPartitionCount(cmd.getPartitionCount())
            .setReplicationFactor(cmd.getReplicationFactor())
            .setStatus(TopicMetadata.TopicStatus.valueOf(cmd.getStatus()))
            .setAssignment(cmd.getAssignment())
            .setTarget(cmd.getTarget());
    stateWriter.appendFollowUpEvent(command.getKey(), MetadataIntent.TOPIC_REGISTERED, event);
  }
}
