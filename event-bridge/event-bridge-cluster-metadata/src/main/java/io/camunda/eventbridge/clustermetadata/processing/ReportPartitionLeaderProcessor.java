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
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata.TopicStatus;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.eventbridge.protocol.request.coordination.ReportPartitionLeaderResponse;
import io.camunda.eventbridge.stream.TypedRecordProcessor;
import io.camunda.eventbridge.stream.Writers;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.HashSet;

/**
 * Handles {@code REPORT_PARTITION_LEADER}: a topic partition's elected Raft leader reporting itself
 * (and its term) so leadership is recorded in replicated state. As with every processor it always
 * writes an event or a rejection.
 *
 * <p>{@link TopicValidator#validateReportLeader} guards it (topic registered and not deleting,
 * partition in range, and the leader-epoch check rejecting a stale term from a deposed leader). On
 * success it appends {@code PARTITION_LEADER_REPORTED} (the applier records {@code leader + term}),
 * then <b>derives readiness</b>: once every partition of a {@code CREATING} topic has a recorded
 * leader, it appends a {@code TOPIC_REGISTERED} flipping the topic to {@code ACTIVE}. Because
 * coverage lives in replicated state, a new metadata leader re-derives the same result after
 * failover.
 */
public final class ReportPartitionLeaderProcessor implements TypedRecordProcessor<TopicRecord> {

  private final Writers writers;
  private final TopicValidator validator;
  private final TopicState topicState;

  public ReportPartitionLeaderProcessor(
      final Writers writers, final TopicValidator validator, final TopicState topicState) {
    this.writers = writers;
    this.validator = validator;
    this.topicState = topicState;
  }

  @Override
  public void processRecord(final TypedRecord<TopicRecord> command) {
    validator
        .validateReportLeader(command.getValue())
        .ifRightOrLeft(ok -> report(command), rejection -> reject(command, rejection));
  }

  private void report(final TypedRecord<TopicRecord> command) {
    final var cmd = command.getValue();
    writers
        .state()
        .appendFollowUpEvent(
            command.getKey(),
            MetadataIntent.PARTITION_LEADER_REPORTED,
            new TopicRecord()
                .setName(cmd.getName())
                .setPartitionId(cmd.getPartitionId())
                .setLeaderNode(cmd.getLeaderNode())
                .setLeaderTerm(cmd.getLeaderTerm()));

    // Derive readiness: once every partition has a leader, a CREATING topic becomes ACTIVE.
    final var topic = topicState.get(cmd.getName());
    final var covered = new HashSet<>(topicState.partitionsWithLeader(cmd.getName()));
    covered.add(cmd.getPartitionId());
    if (topic.status() == TopicStatus.CREATING && covered.size() == topic.partitionCount()) {
      writers
          .state()
          .appendFollowUpEvent(
              command.getKey(),
              MetadataIntent.TOPIC_REGISTERED,
              new TopicRecord()
                  .setName(cmd.getName())
                  .setOp(TopicRecord.OP_REGISTER)
                  .setPartitionCount(topic.partitionCount())
                  .setReplicationFactor(topic.replicationFactor())
                  .setStatus(TopicStatus.ACTIVE)
                  .setAssignment(topic.assignment())
                  .setTarget(topic.target())
                  .setPassive(topic.passive()));
    }

    writers
        .response()
        .respond(
            command, new ReportPartitionLeaderResponse().setErrorCode(CoordinationErrorCode.NONE));
  }

  private void reject(final TypedRecord<TopicRecord> command, final Rejection rejection) {
    writers.rejection().appendRejection(command, rejection.rejectionType(), rejection.reason());
    writers.response().writeRejection(command, rejection.rejectionType(), rejection.reason());
  }
}
