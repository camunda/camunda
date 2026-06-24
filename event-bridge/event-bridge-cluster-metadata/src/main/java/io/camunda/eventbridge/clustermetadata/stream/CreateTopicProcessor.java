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
import java.util.regex.Pattern;

/**
 * Handles the {@code CREATE_TOPIC} command. Validation happens here (not in the manager): the name
 * must be valid, partition/replication counts positive, and the topic must not already exist in the
 * replicated registry — a serialized check, unlike the old racy in-memory snapshot check. The
 * placement is computed by the manager and carried in the command. On success it appends {@code
 * TOPIC_REGISTERED} (status {@code CREATING}) and replies {@code NONE}; otherwise it replies with
 * the error code and writes no event.
 */
final class CreateTopicProcessor implements TypedRecordProcessor<TopicRecord> {

  private static final Pattern TOPIC_NAME = Pattern.compile("[a-zA-Z0-9._-]{1,249}");

  private final Writers writers;
  private final DbTopicState topicState;

  CreateTopicProcessor(final Writers writers, final DbTopicState topicState) {
    this.writers = writers;
    this.topicState = topicState;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();
    final var name = cmd.getName();

    final var error = validate(cmd, name);
    if (error != CoordinationErrorCode.NONE) {
      writers.response().respond(command, new CreateTopicResponse().setErrorCode(error));
      return;
    }

    final var event =
        new TopicRecord()
            .setName(name)
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

  private CoordinationErrorCode validate(final TopicRecord cmd, final String name) {
    if (name == null || !TOPIC_NAME.matcher(name).matches()) {
      return CoordinationErrorCode.INVALID_TOPIC;
    }
    if (cmd.getPartitionCount() < 1 || cmd.getReplicationFactor() < 1) {
      return CoordinationErrorCode.INVALID_TOPIC;
    }
    if (topicState.get(name) != null) {
      return CoordinationErrorCode.TOPIC_ALREADY_EXISTS;
    }
    return CoordinationErrorCode.NONE;
  }
}
