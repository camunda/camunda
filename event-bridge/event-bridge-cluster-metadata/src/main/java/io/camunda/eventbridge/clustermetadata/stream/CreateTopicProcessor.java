/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.CreateTopicResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code CREATE_TOPIC} command. Validation (delegated to {@link TopicValidator})
 * happens here, not in the manager: the name must be valid, counts positive, and the topic must not
 * already exist in the replicated registry — a serialized check, unlike the old racy in-memory
 * snapshot check. The placement is computed by the manager and carried in the command. On success
 * it appends {@code TOPIC_REGISTERED} (status {@code CREATING}) and replies {@code NONE}; otherwise
 * it replies with the error code and writes no event.
 */
final class CreateTopicProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;
  private final TopicValidator validator;

  CreateTopicProcessor(final Writers writers, final TopicValidator validator) {
    this.writers = writers;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();

    final var error = validator.validateCreate(cmd);
    if (error != CoordinationErrorCode.NONE) {
      writers.response().respond(command, new CreateTopicResponse().setErrorCode(error));
      return;
    }

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

    writers
        .response()
        .respond(command, new CreateTopicResponse().setErrorCode(CoordinationErrorCode.NONE));
  }
}
