/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.jspecify.annotations.NullMarked;

/** A buffered chunk of a policy update, with its policy data. */
@NullMarked
public final class PersistedPolicyUpdateChunk extends UnpackedObject implements DbValue {

  private final ObjectProperty<PolicyUpdateRecord> chunkProp =
      new ObjectProperty<>("chunk", new PolicyUpdateRecord());

  public PersistedPolicyUpdateChunk() {
    super(1);
    declareProperty(chunkProp);
  }

  public PolicyUpdateRecord getChunk() {
    return chunkProp.getValue();
  }

  public PersistedPolicyUpdateChunk setChunk(final PolicyUpdateRecord chunk) {
    BufferUtil.copy(chunk, chunkProp.getValue());
    return this;
  }
}
