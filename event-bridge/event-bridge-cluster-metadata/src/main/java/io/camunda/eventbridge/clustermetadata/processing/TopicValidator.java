/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.immutable.TopicState;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.zeebe.util.Either;
import java.util.regex.Pattern;

/**
 * Topic-command validation, in the engine style: each check returns {@code Either<Rejection, T>}
 * and the per-command validations chain them with {@link Either#flatMap}, short-circuiting on the
 * first rejection. Processors compose the result via {@code ifRightOrLeft} (append an event on the
 * right, a rejection on the left). All checks read the replicated {@link TopicState}; this runs on
 * the stream-processing actor.
 */
public final class TopicValidator {

  private static final Pattern TOPIC_NAME = Pattern.compile("[a-zA-Z0-9._-]{1,249}");
  private static final Either<Rejection, Void> VALID = Either.right(null);

  private final TopicState topicState;

  public TopicValidator(final TopicState topicState) {
    this.topicState = topicState;
  }

  Either<Rejection, Void> validateCreate(final TopicRecord command) {
    return nameValid(command)
        .flatMap(ok -> countsPositive(command))
        .flatMap(ok -> topicAbsent(command));
  }

  Either<Rejection, Void> validateReassign(final TopicRecord command) {
    return topicExists(command).flatMap(ok -> replicationFactorPositive(command));
  }

  Either<Rejection, Void> validateDelete(final TopicRecord command) {
    return topicExists(command);
  }

  private Either<Rejection, Void> nameValid(final TopicRecord command) {
    final var name = command.getName();
    if (name == null || !TOPIC_NAME.matcher(name).matches()) {
      return Either.left(new Rejection(CoordinationErrorCode.INVALID_TOPIC, "invalid topic name"));
    }
    return VALID;
  }

  private Either<Rejection, Void> countsPositive(final TopicRecord command) {
    if (command.getPartitionCount() < 1 || command.getReplicationFactor() < 1) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.INVALID_TOPIC,
              "partition count and replication factor must be >= 1"));
    }
    return VALID;
  }

  private Either<Rejection, Void> replicationFactorPositive(final TopicRecord command) {
    if (command.getReplicationFactor() < 1) {
      return Either.left(
          new Rejection(CoordinationErrorCode.INVALID_TOPIC, "replication factor must be >= 1"));
    }
    return VALID;
  }

  private Either<Rejection, Void> topicAbsent(final TopicRecord command) {
    if (topicState.get(command.getName()) != null) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.TOPIC_ALREADY_EXISTS,
              "topic '%s' already exists".formatted(command.getName())));
    }
    return VALID;
  }

  private Either<Rejection, Void> topicExists(final TopicRecord command) {
    if (topicState.get(command.getName()) == null) {
      return Either.left(
          new Rejection(
              CoordinationErrorCode.TOPIC_NOT_FOUND,
              "topic '%s' not found".formatted(command.getName())));
    }
    return VALID;
  }
}
