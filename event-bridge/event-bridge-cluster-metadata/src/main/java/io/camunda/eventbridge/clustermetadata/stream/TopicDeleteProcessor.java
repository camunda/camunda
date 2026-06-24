/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.DeleteTopicResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code DELETE_TOPIC} command. The topic must exist (validated here against the
 * replicated registry); on success it appends {@code TOPIC_DELETED} (applied by {@link
 * TopicDeletedApplier}) and replies {@code NONE}, otherwise replies {@code TOPIC_NOT_FOUND} and
 * writes no event.
 */
final class TopicDeleteProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;
  private final DbTopicState topicState;

  TopicDeleteProcessor(final Writers writers, final DbTopicState topicState) {
    this.writers = writers;
    this.topicState = topicState;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();

    if (topicState.get(cmd.getName()) == null) {
      writers
          .response()
          .respond(
              command,
              new DeleteTopicResponse().setErrorCode(CoordinationErrorCode.TOPIC_NOT_FOUND));
      return;
    }

    final var event = new TopicRecord().setName(cmd.getName()).setOp(cmd.getOp());
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.TOPIC_DELETED, event);

    writers
        .response()
        .respond(command, new DeleteTopicResponse().setErrorCode(CoordinationErrorCode.NONE));
  }
}
