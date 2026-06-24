/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.record.MetadataIntent;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.clustermetadata.state.appliers.TopicRegisteredApplier;

import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code REGISTER_TOPIC} command: turns it into a {@code TOPIC_REGISTERED} follow-up
 * event, which {@link TopicRegisteredApplier} applies to the replicated registry. Holds no state.
 */
public final class TopicRegisterProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;

  public TopicRegisterProcessor(final Writers writers) {
    this.writers = writers;
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
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.TOPIC_REGISTERED, event);
  }
}
