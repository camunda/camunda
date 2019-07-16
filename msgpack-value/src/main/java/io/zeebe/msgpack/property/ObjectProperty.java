/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.ObjectValue;

public class ObjectProperty<T extends ObjectValue> extends BaseProperty<T> {
  public ObjectProperty(String key, T objectValue) {
    super(key, objectValue, objectValue);
  }

  public T getValue() {
    return resolveValue();
  }
}
