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
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handles the {@code CREATE_TOPIC} command. Validation is fenced against the replicated registry: a
 * valid name, positive counts, and a not-already-existing topic. As with every command processor it
 * always results in an event or a rejection, then replies: on success it resolves the placement
 * from the live broker membership, appends {@code TOPIC_REGISTERED} (status {@code CREATING}) with
 * that assignment and replies {@code NONE}; on failure it appends a rejection record and replies
 * with the error code. The leader that produces the durable event decides the placement.
 */
public final class CreateTopicProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;
  private final TopicValidator validator;
  private final PlacementStrategy placement;
  private final Supplier<List<Integer>> registeredBrokers;

  public CreateTopicProcessor(
      final Writers writers,
      final TopicValidator validator,
      final PlacementStrategy placement,
      final Supplier<List<Integer>> registeredBrokers) {
    this.writers = writers;
    this.validator = validator;
    this.placement = placement;
    this.registeredBrokers = registeredBrokers;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    validator
        .validateCreate(command.getValue())
        .ifRightOrLeft(ok -> create(command), rejection -> reject(command, rejection));
  }

  private void create(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();
    final var assignment =
        placement.assign(
            cmd.getName(),
            cmd.getPartitionCount(),
            cmd.getReplicationFactor(),
            registeredBrokers.get());
    final var event =
        new TopicRecord()
            .setName(cmd.getName())
            .setOp(cmd.getOp())
            .setPartitionCount(cmd.getPartitionCount())
            .setReplicationFactor(cmd.getReplicationFactor())
            .setStatus(TopicMetadata.TopicStatus.CREATING)
            .setAssignment(assignment);
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.TOPIC_REGISTERED, event);
    writers
        .response()
        .respond(command, new CreateTopicResponse().setErrorCode(CoordinationErrorCode.NONE));
  }

  private void reject(final TypedRecord<TopicRecord> command, final Rejection rejection) {
    writers.rejection().appendRejection(command, rejection.rejectionType(), rejection.reason());
    writers.response().writeRejection(command, rejection.rejectionType(), rejection.reason());
  }
}
