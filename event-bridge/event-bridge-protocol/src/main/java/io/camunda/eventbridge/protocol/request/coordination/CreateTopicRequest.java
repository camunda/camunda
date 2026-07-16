/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/** Requests creation of a topic with the given partition count and replication factor. */
public class CreateTopicRequest extends UnpackedObject {

  private final StringProperty nameProp = new StringProperty("name", "");
  private final IntegerProperty partitionCountProp = new IntegerProperty("partitionCount", 0);
  private final IntegerProperty replicationFactorProp = new IntegerProperty("replicationFactor", 0);
  // Retention policy (event-bridge ADR 0001); defaults to DELETE (today's behavior) so a request
  // encoded before this field existed decodes unchanged.
  private final EnumProperty<CleanupPolicy> cleanupPolicyProp =
      new EnumProperty<>("cleanupPolicy", CleanupPolicy.class, CleanupPolicy.DELETE);

  public CreateTopicRequest() {
    super(4);
    declareProperty(nameProp)
        .declareProperty(partitionCountProp)
        .declareProperty(replicationFactorProp)
        .declareProperty(cleanupPolicyProp);
  }

  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  public CreateTopicRequest setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public int getPartitionCount() {
    return partitionCountProp.getValue();
  }

  public CreateTopicRequest setPartitionCount(final int partitionCount) {
    partitionCountProp.setValue(partitionCount);
    return this;
  }

  public int getReplicationFactor() {
    return replicationFactorProp.getValue();
  }

  public CreateTopicRequest setReplicationFactor(final int replicationFactor) {
    replicationFactorProp.setValue(replicationFactor);
    return this;
  }

  /** The topic's retention policy (event-bridge ADR 0001); {@code DELETE} unless set otherwise. */
  public CleanupPolicy getCleanupPolicy() {
    return cleanupPolicyProp.getValue();
  }

  public CreateTopicRequest setCleanupPolicy(final CleanupPolicy cleanupPolicy) {
    cleanupPolicyProp.setValue(cleanupPolicy);
    return this;
  }
}
