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
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecordV1;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.jspecify.annotations.NullMarked;

/** A buffered chunk of a policy update, with its policy data. */
/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
@NullMarked
public final class PersistedPolicyUpdateChunkV1 extends UnpackedObject implements DbValue {

  private final ObjectProperty<PolicyUpdateRecordV1> chunkProp =
      new ObjectProperty<>("chunk", new PolicyUpdateRecordV1());

  public PersistedPolicyUpdateChunkV1() {
    super(1);
    declareProperty(chunkProp);
  }

  public PolicyUpdateRecordV1 getChunk() {
    return chunkProp.getValue();
  }

  public PersistedPolicyUpdateChunkV1 setChunk(final PolicyUpdateRecordV1 chunk) {
    BufferUtil.copy(chunk, chunkProp.getValue());
    return this;
  }
}
