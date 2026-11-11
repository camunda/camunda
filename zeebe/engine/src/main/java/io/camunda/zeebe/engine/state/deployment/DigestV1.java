/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.util.VisibleForTesting;
import org.agrona.DirectBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
@VisibleForTesting
public class DigestV1 extends UnpackedObject implements DbValue {
  private final BinaryProperty digestProp = new BinaryProperty("digest");

  public DigestV1() {
    super(1);
    declareProperty(digestProp);
  }

  public DirectBuffer get() {
    return digestProp.getValue();
  }

  public DigestV1 set(final DirectBuffer digest) {
    digestProp.setValue(digest);
    return this;
  }
}
