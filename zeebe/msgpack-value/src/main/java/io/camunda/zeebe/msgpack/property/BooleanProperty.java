/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.value.BooleanValue;
import io.camunda.zeebe.msgpack.value.StringValue;

public final class BooleanProperty extends BaseProperty<BooleanValue> {

  public BooleanProperty(final String key) {
    super(key, new BooleanValue());
  }

  public BooleanProperty(final String key, final boolean defaultValue) {
    super(key, new BooleanValue(), new BooleanValue(defaultValue));
  }

  public BooleanProperty(final StringValue key) {
    super(key, new BooleanValue());
  }

  public BooleanProperty(final StringValue key, final boolean defaultValue) {
    super(key, new BooleanValue(), new BooleanValue(defaultValue));
  }

  public boolean getValue() {
    return resolveValue().getValue();
  }

  public void setValue(final boolean value) {
    this.value.setValue(value);
    isSet = true;
  }
}
