/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.appliers;

import io.camunda.eventbridge.clustermetadata.record.TopicRecord;
import io.camunda.eventbridge.clustermetadata.state.mutable.MutableTopicState;
import io.camunda.eventbridge.stream.TypedEventApplier;
import io.camunda.zeebe.protocol.record.intent.MetadataIntent;

/**
 * Applies {@code PARTITION_LEADER_REPORTED} events — records a topic partition's elected Raft
 * leader (node id + term) in replicated state. Runs identically on leader (after the processor) and
 * follower/observer (on replay), so every replica converges on the same leadership view.
 */
public final class PartitionLeaderReportedApplier
    implements TypedEventApplier<MetadataIntent, TopicRecord> {

  private final MutableTopicState topicState;

  public PartitionLeaderReportedApplier(final MutableTopicState topicState) {
    this.topicState = topicState;
  }

  @Override
  public void applyState(final long key, final TopicRecord value) {
    topicState.recordPartitionLeader(
        value.getName(), value.getPartitionId(), value.getLeaderNode(), value.getLeaderTerm());
  }
}
