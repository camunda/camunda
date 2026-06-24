/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.topic;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One partition's ordered replica node ids — the nested msgpack element of a topic's {@code
 * assignment}/{@code target} arrays (the event-bridge counterpart of consumer-groups' {@code
 * MemberAssignment}). The static {@link #write}/{@link #read} helpers convert between an {@code
 * ArrayProperty<PartitionReplicas>} and the {@code partition → replicas} map the rest of the code
 * works with, so a topic's placement is replicated as structured msgpack rather than an encoded
 * string.
 */
public final class PartitionReplicas extends ObjectValue {

  private final IntegerProperty partitionProp = new IntegerProperty("partition", 0);
  private final ArrayProperty<IntegerValue> replicasProp =
      new ArrayProperty<>("replicas", IntegerValue::new);

  public PartitionReplicas() {
    super(2);
    declareProperty(partitionProp).declareProperty(replicasProp);
  }

  public int getPartition() {
    return partitionProp.getValue();
  }

  public PartitionReplicas setPartition(final int partition) {
    partitionProp.setValue(partition);
    return this;
  }

  public List<Integer> getReplicas() {
    return replicasProp.stream().map(IntegerValue::getValue).toList();
  }

  public PartitionReplicas setReplicas(final List<Integer> replicas) {
    replicasProp.reset();
    replicas.forEach(node -> replicasProp.add().setValue(node));
    return this;
  }

  /**
   * Replaces {@code property} with the {@code partition → replicas} entries of {@code assignment}.
   */
  public static void write(
      final ArrayProperty<PartitionReplicas> property,
      final Map<Integer, List<Integer>> assignment) {
    property.reset();
    assignment.forEach(
        (partition, replicas) -> property.add().setPartition(partition).setReplicas(replicas));
  }

  /** Reads an {@code ArrayProperty<PartitionReplicas>} into a {@code partition → replicas} map. */
  public static Map<Integer, List<Integer>> read(final ArrayProperty<PartitionReplicas> property) {
    final Map<Integer, List<Integer>> assignment = new LinkedHashMap<>();
    property.forEach(entry -> assignment.put(entry.getPartition(), entry.getReplicas()));
    return assignment;
  }
}
