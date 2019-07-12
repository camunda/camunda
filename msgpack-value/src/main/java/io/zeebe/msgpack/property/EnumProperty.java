/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.EnumValue;

public class EnumProperty<E extends Enum<E>> extends BaseProperty<EnumValue<E>> {
  public EnumProperty(String key, Class<E> type) {
    super(key, new EnumValue<>(type));
  }

  public EnumProperty(String key, Class<E> type, E defaultValue) {
    super(key, new EnumValue<>(type), new EnumValue<>(type, defaultValue));
  }

  public E getValue() {
    return resolveValue().getValue();
  }

  public void setValue(E value) {
    this.value.setValue(value);
    this.isSet = true;
  }
}
