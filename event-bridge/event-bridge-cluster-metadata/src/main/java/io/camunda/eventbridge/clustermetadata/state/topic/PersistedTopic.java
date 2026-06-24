/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.state.topic;

import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata.TopicStatus;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;

/**
 * Per-topic replicated state stored in the {@link
 * io.camunda.eventbridge.clustermetadata.state.MetadataColumnFamilies#TOPIC_REGISTRY} column family
 * — the msgpack {@link DbValue} backing the registry (the event-bridge counterpart of
 * consumer-groups' {@code GroupState}/{@code MemberState}). Replaces the previous hand-rolled
 * string encoding: the placement is held as structured {@link PartitionReplicas} arrays.
 */
public final class PersistedTopic extends UnpackedObject implements DbValue {

  private final IntegerProperty partitionCountProp = new IntegerProperty("partitionCount", 0);
  private final IntegerProperty replicationFactorProp = new IntegerProperty("replicationFactor", 0);
  private final EnumProperty<TopicStatus> statusProp =
      new EnumProperty<>("status", TopicStatus.class, TopicStatus.CREATING);
  private final ArrayProperty<PartitionReplicas> assignmentProp =
      new ArrayProperty<>("assignment", PartitionReplicas::new);
  private final ArrayProperty<PartitionReplicas> targetProp =
      new ArrayProperty<>("target", PartitionReplicas::new);

  public PersistedTopic() {
    super(5);
    declareProperty(partitionCountProp)
        .declareProperty(replicationFactorProp)
        .declareProperty(statusProp)
        .declareProperty(assignmentProp)
        .declareProperty(targetProp);
  }

  /** Wraps a {@link TopicMetadata} for storage. */
  public PersistedTopic wrap(final TopicMetadata metadata) {
    partitionCountProp.setValue(metadata.partitionCount());
    replicationFactorProp.setValue(metadata.replicationFactor());
    statusProp.setValue(metadata.status());
    PartitionReplicas.write(assignmentProp, metadata.assignment());
    PartitionReplicas.write(targetProp, metadata.target());
    return this;
  }

  public TopicMetadata toMetadata() {
    return new TopicMetadata(
        partitionCountProp.getValue(),
        replicationFactorProp.getValue(),
        statusProp.getValue(),
        PartitionReplicas.read(assignmentProp),
        PartitionReplicas.read(targetProp));
  }
}
