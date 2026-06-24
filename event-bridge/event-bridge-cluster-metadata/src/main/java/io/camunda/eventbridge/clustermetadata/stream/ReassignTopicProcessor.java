/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code REASSIGN_TOPIC} command. The topic must exist (validated here against the
 * replicated registry) and the replication factor must be positive; the new target placement is
 * computed by the manager and carried in the command. On success it appends {@code
 * TOPIC_REGISTERED} with the new target (the change-coordinator drives committed → target) and
 * replies {@code NONE}; otherwise it replies with the error code.
 */
final class ReassignTopicProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;
  private final DbTopicState topicState;

  ReassignTopicProcessor(final Writers writers, final DbTopicState topicState) {
    this.writers = writers;
    this.topicState = topicState;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();

    final var error = validate(cmd);
    if (error != CoordinationErrorCode.NONE) {
      writers.response().respond(command, new ReassignTopicResponse().setErrorCode(error));
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
        .respond(command, new ReassignTopicResponse().setErrorCode(CoordinationErrorCode.NONE));
  }

  private CoordinationErrorCode validate(final TopicRecord cmd) {
    if (topicState.get(cmd.getName()) == null) {
      return CoordinationErrorCode.TOPIC_NOT_FOUND;
    }
    if (cmd.getReplicationFactor() < 1) {
      return CoordinationErrorCode.INVALID_TOPIC;
    }
    return CoordinationErrorCode.NONE;
  }
}
