/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.stream;

import io.camunda.eventbridge.stream.StreamRecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;

/**
 * Command processor for the topic registry on the metadata partition. Following the engine's
 * command-processor / event-applier split, it does <em>not</em> mutate state directly: on a {@code
 * REGISTER}/{@code DELETE} command it appends the matching {@code TOPIC_REGISTERED}/{@code
 * TOPIC_DELETED} follow-up event via the {@link io.camunda.eventbridge.stream.StateWriter}, which
 * both writes the event and applies it through the registered {@link
 * io.camunda.eventbridge.stream.TypedEventApplier}. The same appliers run on replay, so every
 * replica converges and a new leader restores the registry after failover.
 *
 * <p>Register vs delete is read from the command's own {@code op} field (see {@link TopicRecord}).
 */
public final class TopicProcessor extends StreamRecordProcessor {

  public TopicProcessor(
      final DbTopicState topicState, final Map<String, TopicMetadata> registryCache) {
    super(MetadataRecordValues.TOPIC_VALUE_TYPE);
    appliers()
        .register(
            MetadataIntent.TOPIC_REGISTERED, new TopicRegisteredApplier(topicState, registryCache))
        .register(MetadataIntent.TOPIC_DELETED, new TopicDeletedApplier(topicState, registryCache));
  }

  @Override
  protected void processCommand(final TypedRecord command) {
    final var cmd = (TopicRecord) command.getValue();

    // Command handling produces the follow-up event; the registered applier (not the command)
    // mutates state, so the leader applies exactly what a follower will replay.
    final var event =
        new TopicRecord()
            .setName(cmd.getName())
            .setOp(cmd.getOp())
            .setPartitionCount(cmd.getPartitionCount())
            .setReplicationFactor(cmd.getReplicationFactor())
            .setStatus(TopicMetadata.TopicStatus.valueOf(cmd.getStatus()))
            .setAssignment(cmd.getAssignment())
            .setTarget(cmd.getTarget());
    final var intent =
        cmd.isDelete() ? MetadataIntent.TOPIC_DELETED : MetadataIntent.TOPIC_REGISTERED;

    stateWriter().appendFollowUpEvent(command.getKey(), intent, event);
  }
}
