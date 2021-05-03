/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.value.ObjectValue;

public final class ObjectProperty<T extends ObjectValue> extends BaseProperty<T> {
  public ObjectProperty(final String key, final T objectValue) {
    super(key, objectValue, objectValue);
  }

  public T getValue() {
    return resolveValue();
  }
}
