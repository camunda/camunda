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
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/**
 * Requests a reassignment of a topic to a new replication factor. The coordinator computes the
 * target placement and drives the cluster to it one safe Raft step at a time.
 */
public class ReassignTopicRequest extends UnpackedObject {

  private final StringProperty nameProp = new StringProperty("name", "");
  private final IntegerProperty replicationFactorProp = new IntegerProperty("replicationFactor", 0);

  public ReassignTopicRequest() {
    super(2);
    declareProperty(nameProp).declareProperty(replicationFactorProp);
  }

  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  public ReassignTopicRequest setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public int getReplicationFactor() {
    return replicationFactorProp.getValue();
  }

  public ReassignTopicRequest setReplicationFactor(final int replicationFactor) {
    replicationFactorProp.setValue(replicationFactor);
    return this;
  }
}
