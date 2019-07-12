/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.BooleanValue;

public class BooleanProperty extends BaseProperty<BooleanValue> {

  public BooleanProperty(String key) {
    super(key, new BooleanValue());
  }

  public BooleanProperty(String key, boolean defaultValue) {
    super(key, new BooleanValue(), new BooleanValue(defaultValue));
  }

  public boolean getValue() {
    return resolveValue().getValue();
  }

  public void setValue(boolean value) {
    this.value.setValue(value);
    this.isSet = true;
  }
}
