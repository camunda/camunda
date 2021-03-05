/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.variable.mapping;

import io.zeebe.test.util.JsonUtil;
import java.util.Objects;

public final class VariableValue {

  private final String name;
  private final String value;

  private VariableValue(final String name, final String value) {
    this.name = name;
    this.value = value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableValue that = (VariableValue) o;
    return Objects.equals(name, that.name) && JsonUtil.isEqual(value, that.value);
  }

  @Override
  public String toString() {
    return "{" + "name='" + name + '\'' + ", value='" + value + '\'' + '}';
  }

  public static VariableValue variable(final String name, final String value) {
    return new VariableValue(name, value);
  }
}
