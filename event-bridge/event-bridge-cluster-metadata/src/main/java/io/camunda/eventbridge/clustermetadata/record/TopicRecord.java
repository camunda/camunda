/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.record;

import io.camunda.eventbridge.clustermetadata.state.topic.PartitionReplicas;
import io.camunda.eventbridge.clustermetadata.state.topic.TopicMetadata;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;

/**
 * A mutation of the topic registry on the coordinator partition. The {@code op} field — not the
 * record intent — discriminates register from delete: the stream tags these records with a reused
 * {@link io.camunda.zeebe.protocol.record.ValueType}, so the platform would interpret the intent
 * short under that value type, not as a {@link MetadataIntent}. Carrying the operation in the
 * record keeps replay independent of intent mapping.
 */
public final class TopicRecord extends UnifiedRecordValue {

  /** {@code op} value for a register (create/update). */
  public static final String OP_REGISTER = "register";

  /** {@code op} value for a delete. */
  public static final String OP_DELETE = "delete";

  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty opProp = new StringProperty("op", OP_REGISTER);
  private final IntegerProperty partitionCountProp = new IntegerProperty("partitionCount", 0);
  private final IntegerProperty replicationFactorProp = new IntegerProperty("replicationFactor", 0);
  private final StringProperty statusProp =
      new StringProperty("status", TopicMetadata.TopicStatus.CREATING.name());

  // Centrally-decided placement (partition id -> replica node ids), as structured msgpack.
  private final ArrayProperty<PartitionReplicas> assignmentProp =
      new ArrayProperty<>("assignment", PartitionReplicas::new);
  // In-flight reassignment target (same shape); empty when no reconfiguration is in progress.
  private final ArrayProperty<PartitionReplicas> targetProp =
      new ArrayProperty<>("target", PartitionReplicas::new);

  public TopicRecord() {
    super(7);
    declareProperty(nameProp)
        .declareProperty(opProp)
        .declareProperty(partitionCountProp)
        .declareProperty(replicationFactorProp)
        .declareProperty(statusProp)
        .declareProperty(assignmentProp)
        .declareProperty(targetProp);
  }

  /**
   * The reused {@link ValueType} this record rides on its dedicated Raft partition. {@link
   * UnifiedRecordValue#valueType()} resolves via the engine's class→type map, which doesn't know
   * this event-bridge record (it would return {@code null}); the StreamProcessor's result builder
   * reads <em>this</em> to stamp appended follow-up events, so it must be set explicitly or the
   * event is never written (and followers never replay it).
   */
  @Override
  public ValueType valueType() {
    return MetadataRecordValues.TOPIC_VALUE_TYPE;
  }

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public TopicRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public String getOp() {
    return BufferUtil.bufferAsString(opProp.getValue());
  }

  public TopicRecord setOp(final String op) {
    opProp.setValue(op);
    return this;
  }

  public boolean isDelete() {
    return OP_DELETE.equals(getOp());
  }

  public int getPartitionCount() {
    return partitionCountProp.getValue();
  }

  public TopicRecord setPartitionCount(final int partitionCount) {
    partitionCountProp.setValue(partitionCount);
    return this;
  }

  public int getReplicationFactor() {
    return replicationFactorProp.getValue();
  }

  public TopicRecord setReplicationFactor(final int replicationFactor) {
    replicationFactorProp.setValue(replicationFactor);
    return this;
  }

  public String getStatus() {
    return BufferUtil.bufferAsString(statusProp.getValue());
  }

  public TopicRecord setStatus(final TopicMetadata.TopicStatus status) {
    statusProp.setValue(status.name());
    return this;
  }

  /** The committed placement as {@code partition → replica node ids}. */
  public Map<Integer, List<Integer>> getAssignment() {
    return PartitionReplicas.read(assignmentProp);
  }

  public TopicRecord setAssignment(final Map<Integer, List<Integer>> assignment) {
    PartitionReplicas.write(assignmentProp, assignment);
    return this;
  }

  /** The in-flight reassignment target as {@code partition → replica node ids} (empty when none). */
  public Map<Integer, List<Integer>> getTarget() {
    return PartitionReplicas.read(targetProp);
  }

  public TopicRecord setTarget(final Map<Integer, List<Integer>> target) {
    PartitionReplicas.write(targetProp, target);
    return this;
  }

  /** The register payload as {@link TopicMetadata} (only meaningful for {@code op == register}). */
  public TopicMetadata toMetadata() {
    return new TopicMetadata(
        getPartitionCount(),
        getReplicationFactor(),
        TopicMetadata.TopicStatus.valueOf(getStatus()),
        getAssignment(),
        getTarget());
  }
}
