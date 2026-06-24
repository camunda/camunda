/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import java.util.List;
import java.util.Map;

/** One topic in the {@link ListTopicsResponse}: its config and committed placement. */
public final class TopicEntry extends ObjectValue {

  private final StringProperty nameProp = new StringProperty("name", "");
  private final IntegerProperty partitionCountProp = new IntegerProperty("partitionCount", 0);
  private final IntegerProperty replicationFactorProp =
      new IntegerProperty("replicationFactor", 0);
  private final StringProperty statusProp = new StringProperty("status", "");
  private final ArrayProperty<TopicPartitionReplicas> assignmentProp =
      new ArrayProperty<>("assignment", TopicPartitionReplicas::new);

  public TopicEntry() {
    super(5);
    declareProperty(nameProp)
        .declareProperty(partitionCountProp)
        .declareProperty(replicationFactorProp)
        .declareProperty(statusProp)
        .declareProperty(assignmentProp);
  }

  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  public int getPartitionCount() {
    return partitionCountProp.getValue();
  }

  public int getReplicationFactor() {
    return replicationFactorProp.getValue();
  }

  public String getStatus() {
    return bufferAsString(statusProp.getValue());
  }

  public Map<Integer, List<Integer>> getAssignment() {
    return TopicPartitionReplicas.read(assignmentProp);
  }

  public TopicEntry set(
      final String name,
      final int partitionCount,
      final int replicationFactor,
      final String status,
      final Map<Integer, List<Integer>> assignment) {
    nameProp.setValue(name);
    partitionCountProp.setValue(partitionCount);
    replicationFactorProp.setValue(replicationFactor);
    statusProp.setValue(status);
    TopicPartitionReplicas.write(assignmentProp, assignment);
    return this;
  }
}
