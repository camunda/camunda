/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import org.agrona.DirectBuffer;

/** Hand-written {@link UnpackedObject} with the stored shape of {@link PojoVariable}. */
public final class UnpackedVariable extends UnpackedObject {

  private final LongProperty keyProp = new LongProperty("key");
  private final BinaryProperty valueProp = new BinaryProperty("value");

  public UnpackedVariable() {
    super(2);
    declareProperty(keyProp).declareProperty(valueProp);
  }

  public UnpackedVariable setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  public UnpackedVariable setValue(final DirectBuffer value) {
    valueProp.setValue(value, 0, value.capacity());
    return this;
  }
}
