/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import java.util.Objects;

public final class VariableValueFilter extends ComparableValues implements FilterBase {
  private final String name;

  public VariableValueFilter(
      final String name,
      final Object eq,
      final Object neq,
      final Object gt,
      final Object gte,
      final Object lt,
      final Object lte) {
    super(eq, neq, gt, gte, lt, lte);
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name) + super.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (VariableValueFilter) obj;
    return Objects.equals(name, that.name) && super.equals(that);
  }

  @Override
  public String toString() {
    return "VariableValueFilter["
        + "name="
        + name
        + ", "
        + "eq="
        + eq
        + ", "
        + "neq="
        + neq
        + ", "
        + "gt="
        + gt
        + ", "
        + "gte="
        + gte
        + ", "
        + "lt="
        + lt
        + ", "
        + "lte="
        + lte
        + ']';
  }

  public static final class Builder extends ComparableValueBuilder<VariableValueFilter> {

    private String name;

    public Builder name(final String value) {
      name = value;
      return this;
    }

    @Override
    public VariableValueFilter build() {
      return new VariableValueFilter(Objects.requireNonNull(name), eq, neq, gt, gte, lt, lte);
    }
  }
}
