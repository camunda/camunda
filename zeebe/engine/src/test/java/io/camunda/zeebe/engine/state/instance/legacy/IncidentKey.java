/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance.legacy;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class IncidentKey extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key");

  public IncidentKey() {
    super(1);
    declareProperty(keyProp);
  }

  public long get() {
    return keyProp.getValue();
  }

  public void set(final long key) {
    keyProp.setValue(key);
  }
}
