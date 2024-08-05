/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.filter;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;

public abstract class ComparableValues {
  protected final Object eq;
  protected final Object neq;
  protected final Object gt;
  protected final Object gte;
  protected final Object lt;
  protected final Object lte;

  public ComparableValues(
      final Object eq,
      final Object neq,
      final Object gt,
      final Object gte,
      final Object lt,
      final Object lte) {
    this.eq = eq;
    this.neq = neq;
    this.gt = gt;
    this.gte = gte;
    this.lt = lt;
    this.lte = lte;
  }

  public Object eq() {
    return eq;
  }

  public Object neq() {
    return neq;
  }

  public Object gt() {
    return gt;
  }

  public Object gte() {
    return gte;
  }

  public Object lt() {
    return lt;
  }

  public Object lte() {
    return lte;
  }

  @Override
  public int hashCode() {
    return Objects.hash(eq, neq, gt, gte, lt, lte);
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
    return Objects.equals(eq, that.eq)
        && Objects.equals(neq, that.neq)
        && Objects.equals(gt, that.gt)
        && Objects.equals(gte, that.gte)
        && Objects.equals(lt, that.lt)
        && Objects.equals(lte, that.lte);
  }

  public abstract static class ComparableValueBuilder<T extends ComparableValues>
      implements ObjectBuilder<T> {

    protected Object eq;
    protected Object neq;
    protected Object gt;
    protected Object gte;
    protected Object lt;
    protected Object lte;

    public ComparableValueBuilder<T> eq(final Object value) {
      eq = value;
      return this;
    }

    public ComparableValueBuilder<T> neq(final Object value) {
      neq = value;
      return this;
    }

    public ComparableValueBuilder<T> gt(final Object value) {
      gt = value;
      return this;
    }

    public ComparableValueBuilder<T> gte(final Object value) {
      gte = value;
      return this;
    }

    public ComparableValueBuilder<T> lt(final Object value) {
      lt = value;
      return this;
    }

    public ComparableValueBuilder<T> lte(final Object value) {
      lte = value;
      return this;
    }

    @Override
    public abstract T build();
  }
}
