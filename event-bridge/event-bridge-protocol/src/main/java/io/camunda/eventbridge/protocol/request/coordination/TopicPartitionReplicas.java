/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One partition's ordered replica node ids — the nested element of a {@link TopicEntry}'s assignment
 * in the {@link ListTopicsResponse}. Carried as structured msgpack rather than an encoded string.
 */
public final class TopicPartitionReplicas extends ObjectValue {

  private final IntegerProperty partitionProp = new IntegerProperty("partition", 0);
  private final ArrayProperty<IntegerValue> replicasProp =
      new ArrayProperty<>("replicas", IntegerValue::new);

  public TopicPartitionReplicas() {
    super(2);
    declareProperty(partitionProp).declareProperty(replicasProp);
  }

  public int getPartition() {
    return partitionProp.getValue();
  }

  public TopicPartitionReplicas setPartition(final int partition) {
    partitionProp.setValue(partition);
    return this;
  }

  public List<Integer> getReplicas() {
    return replicasProp.stream().map(IntegerValue::getValue).toList();
  }

  public TopicPartitionReplicas setReplicas(final List<Integer> replicas) {
    replicasProp.reset();
    replicas.forEach(node -> replicasProp.add().setValue(node));
    return this;
  }

  static void write(
      final ArrayProperty<TopicPartitionReplicas> property,
      final Map<Integer, List<Integer>> assignment) {
    property.reset();
    assignment.forEach(
        (partition, replicas) -> property.add().setPartition(partition).setReplicas(replicas));
  }

  static Map<Integer, List<Integer>> read(final ArrayProperty<TopicPartitionReplicas> property) {
    final Map<Integer, List<Integer>> assignment = new LinkedHashMap<>();
    property.forEach(entry -> assignment.put(entry.getPartition(), entry.getReplicas()));
    return assignment;
  }
}
