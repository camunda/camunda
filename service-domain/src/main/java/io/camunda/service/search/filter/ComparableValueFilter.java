/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

public final class ComparableValueFilter extends ComparableValues implements FilterBase {

  private ComparableValueFilter(
      final Object eq,
      final Object neq,
      final Object gt,
      final Object gte,
      final Object lt,
      final Object lte) {
    super(eq, neq, gt, gte, lt, lte);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    return super.equals(obj);
  }

  @Override
  public String toString() {
    return "ComparableValueFilter["
        + "eq="
        + eq
        + ", neq="
        + neq
        + ", gt="
        + gt
        + ", gte="
        + gte
        + ", lt="
        + lt
        + ", lte="
        + lte
        + ']';
  }

  public static final class Builder extends ComparableValueBuilder<ComparableValueFilter> {

    @Override
    public ComparableValueFilter build() {
      return new ComparableValueFilter(eq, neq, gt, gte, lt, lte);
    }
  }
}
