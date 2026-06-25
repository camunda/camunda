/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.placement.PlacementStrategy;
import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.immutable.TopicState;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handles the {@code REASSIGN_TOPIC} command. Validation: the topic must exist and the replication
 * factor must be positive. On success it resolves the new target placement from the topic's current
 * partition count + the requested replication factor against the live broker membership, appends
 * {@code TOPIC_REGISTERED} carrying the current assignment plus that target (the change-coordinator
 * drives committed → target) and replies {@code NONE}; on failure it appends a rejection and
 * replies with the error code. The leader that produces the durable event decides the target.
 */
public final class ReassignTopicProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;
  private final TopicValidator validator;
  private final TopicState topicState;
  private final PlacementStrategy placement;
  private final Supplier<List<Integer>> registeredBrokers;

  public ReassignTopicProcessor(
      final Writers writers,
      final TopicValidator validator,
      final TopicState topicState,
      final PlacementStrategy placement,
      final Supplier<List<Integer>> registeredBrokers) {
    this.writers = writers;
    this.validator = validator;
    this.topicState = topicState;
    this.placement = placement;
    this.registeredBrokers = registeredBrokers;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    validator
        .validateReassign(command.getValue())
        .ifRightOrLeft(ok -> reassign(command), rejection -> reject(command, rejection));
  }

  private void reassign(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();
    // The topic exists (validated) and is read on this actor from the replicated registry.
    final var meta = topicState.get(cmd.getName());
    final var target =
        placement.assign(
            meta.partitionCount(), cmd.getReplicationFactor(), registeredBrokers.get());
    final var event =
        new TopicRecord()
            .setName(cmd.getName())
            .setOp(cmd.getOp())
            .setPartitionCount(meta.partitionCount())
            .setReplicationFactor(cmd.getReplicationFactor())
            .setStatus(meta.status())
            .setAssignment(meta.assignment())
            .setTarget(target);
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.TOPIC_REGISTERED, event);
    writers
        .response()
        .respond(command, new ReassignTopicResponse().setErrorCode(CoordinationErrorCode.NONE));
  }

  private void reject(final TypedRecord<TopicRecord> command, final Rejection rejection) {
    writers.rejection().appendRejection(command, rejection.rejectionType(), rejection.reason());
    writers.response().writeRejection(command, rejection.rejectionType(), rejection.reason());
  }
}
