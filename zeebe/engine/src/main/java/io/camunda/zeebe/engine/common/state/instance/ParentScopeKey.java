/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

public class ParentScopeKey extends UnpackedObject implements DbValue {
  private final LongProperty keyProp = new LongProperty("parentScopeKey", -1L);

  public ParentScopeKey() {
    super(1);
    declareProperty(keyProp);
  }

  public void set(final long key) {
    keyProp.setValue(key);
  }

  public long get() {
    return keyProp.getValue();
  }
}
