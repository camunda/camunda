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
 * Handles the {@code DELETE_TOPIC} command. Validation (delegated to {@link TopicValidator}): the
 * topic must exist. On success it appends {@code TOPIC_DELETED} (applied by {@link
 * TopicDeletedApplier}) and replies {@code NONE}, otherwise replies {@code TOPIC_NOT_FOUND} and
 * writes no event.
 */
final class TopicDeleteProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;
  private final TopicValidator validator;

  TopicDeleteProcessor(final Writers writers, final TopicValidator validator) {
    this.writers = writers;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();

    final var error = validator.validateDelete(cmd);
    if (error != CoordinationErrorCode.NONE) {
      writers.response().respond(command, new DeleteTopicResponse().setErrorCode(error));
      return;
    }

    final var event = new TopicRecord().setName(cmd.getName()).setOp(cmd.getOp());
    writers.state().appendFollowUpEvent(command.getKey(), MetadataIntent.TOPIC_DELETED, event);

    writers
        .response()
        .respond(command, new DeleteTopicResponse().setErrorCode(CoordinationErrorCode.NONE));
  }
}
