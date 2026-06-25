/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/**
 * A topic partition's elected Raft leader reporting itself to the metadata-group leader, so
 * leadership is recorded in replicated state. The {@code term} lets the leader reject a stale
 * report from a deposed leader (the leader-epoch guard).
 */
public class ReportPartitionLeaderRequest extends UnpackedObject {

  private final StringProperty topicProp = new StringProperty("topic", "");
  private final IntegerProperty partitionIdProp = new IntegerProperty("partitionId", 0);
  private final IntegerProperty leaderNodeProp = new IntegerProperty("leaderNode", -1);
  private final LongProperty termProp = new LongProperty("term", -1L);

  public ReportPartitionLeaderRequest() {
    super(4);
    declareProperty(topicProp)
        .declareProperty(partitionIdProp)
        .declareProperty(leaderNodeProp)
        .declareProperty(termProp);
  }

  public String getTopic() {
    return io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString(topicProp.getValue());
  }

  public ReportPartitionLeaderRequest setTopic(final String topic) {
    topicProp.setValue(topic);
    return this;
  }

  public int getPartitionId() {
    return partitionIdProp.getValue();
  }

  public ReportPartitionLeaderRequest setPartitionId(final int partitionId) {
    partitionIdProp.setValue(partitionId);
    return this;
  }

  public int getLeaderNode() {
    return leaderNodeProp.getValue();
  }

  public ReportPartitionLeaderRequest setLeaderNode(final int leaderNode) {
    leaderNodeProp.setValue(leaderNode);
    return this;
  }

  public long getTerm() {
    return termProp.getValue();
  }

  public ReportPartitionLeaderRequest setTerm(final long term) {
    termProp.setValue(term);
    return this;
  }
}
