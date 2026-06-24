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

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.ReassignTopicResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Handles the {@code REASSIGN_TOPIC} command. Validation: the topic must exist and the replication
 * factor must be positive; the new target placement is computed by the manager and carried in the
 * command. On success it appends {@code TOPIC_REGISTERED} with the new target (the
 * change-coordinator drives committed → target) and replies {@code NONE}; on failure it appends a
 * rejection and replies with the error code.
 */
public final class ReassignTopicProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;
  private final TopicValidator validator;

  public ReassignTopicProcessor(final Writers writers, final TopicValidator validator) {
    this.writers = writers;
    this.validator = validator;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    validator
        .validateReassign(command.getValue())
        .ifRightOrLeft(ok -> reassign(command), rejection -> reject(command, rejection));
  }

  private void reassign(final TypedRecord<TopicRecord> command) {
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
    writers
        .response()
        .respond(command, new ReassignTopicResponse().setErrorCode(CoordinationErrorCode.NONE));
  }

  private void reject(final TypedRecord<TopicRecord> command, final Rejection rejection) {
    writers.rejection().appendRejection(command, rejection.rejectionType(), rejection.reason());
    writers.response().respond(command, new ReassignTopicResponse().setErrorCode(rejection.code()));
  }
}
