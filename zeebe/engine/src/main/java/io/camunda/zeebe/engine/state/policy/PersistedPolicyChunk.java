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
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import org.agrona.DirectBuffer;

/** A stored chunk, kept as the serialized chunk record. */
public final class PersistedPolicyChunk extends UnpackedObject implements DbValue {

  private final BinaryProperty chunkProp = new BinaryProperty("chunk");

  public PersistedPolicyChunk() {
    super(1);
    declareProperty(chunkProp);
  }

  public DirectBuffer getChunk() {
    return chunkProp.getValue();
  }

  public PersistedPolicyChunk setChunk(final DirectBuffer chunk) {
    chunkProp.setValue(chunk);
    return this;
  }
}
