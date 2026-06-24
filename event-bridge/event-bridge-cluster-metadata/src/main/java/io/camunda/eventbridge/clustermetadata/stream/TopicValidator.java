/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import java.util.regex.Pattern;

/**
 * Validates topic commands against the replicated registry — the single place the topic-admin
 * processors decide accept-or-reject, mirroring how the Zeebe engine keeps validation in dedicated
 * checkers rather than inline in processors. Each method returns {@link CoordinationErrorCode#NONE}
 * when the command may proceed, or the rejection code otherwise.
 */
final class TopicValidator {

  private static final Pattern TOPIC_NAME = Pattern.compile("[a-zA-Z0-9._-]{1,249}");

  private final DbTopicState topicState;

  TopicValidator(final DbTopicState topicState) {
    this.topicState = topicState;
  }

  CoordinationErrorCode validateCreate(final TopicRecord command) {
    final var name = command.getName();
    if (name == null || !TOPIC_NAME.matcher(name).matches()) {
      return CoordinationErrorCode.INVALID_TOPIC;
    }
    if (command.getPartitionCount() < 1 || command.getReplicationFactor() < 1) {
      return CoordinationErrorCode.INVALID_TOPIC;
    }
    if (topicState.get(name) != null) {
      return CoordinationErrorCode.TOPIC_ALREADY_EXISTS;
    }
    return CoordinationErrorCode.NONE;
  }

  CoordinationErrorCode validateReassign(final TopicRecord command) {
    if (topicState.get(command.getName()) == null) {
      return CoordinationErrorCode.TOPIC_NOT_FOUND;
    }
    if (command.getReplicationFactor() < 1) {
      return CoordinationErrorCode.INVALID_TOPIC;
    }
    return CoordinationErrorCode.NONE;
  }

  CoordinationErrorCode validateDelete(final TopicRecord command) {
    if (topicState.get(command.getName()) == null) {
      return CoordinationErrorCode.TOPIC_NOT_FOUND;
    }
    return CoordinationErrorCode.NONE;
  }
}
