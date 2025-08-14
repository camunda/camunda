/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class TypeReference<T> {
  private final Type type;

  protected TypeReference() {
    final Type superClass = getClass().getGenericSuperclass();
    if (superClass instanceof final ParameterizedType parameterizedType) {
      type = parameterizedType.getActualTypeArguments()[0];
    } else {
      throw new IllegalArgumentException("Missing type parameter.");
    }
  }

  public Type getType() {
    return type;
  }
}
