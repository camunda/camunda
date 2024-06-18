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

public final record VariableValueFilter(
    String name, Object eq, Object neq, Object gt, Object gte, Object lt, Object lte)
    implements FilterBase {


  public static final class Builder implements ObjectBuilder<VariableValueFilter> {

    private String name;
    private Object eq;
    private Object neq;
    private Object gt;
    private Object gte;
    private Object lt;
    private Object lte;

    public Builder name(final String value) {
      name = value;
      return this;
    }

    public Builder eq(final Object value) {
      eq = value;
      return this;
    }

    public Builder neq(final Object value) {
      neq = value;
      return this;
    }

    public Builder gt(final Object value) {
      gt = value;
      return this;
    }

    public Builder gte(final Object value) {
      gte = value;
      return this;
    }

    public Builder lt(final Object value) {
      lt = value;
      return this;
    }

    public Builder lte(final Object value) {
      lte = value;
      return this;
    }

    @Override
    public VariableValueFilter build() {
      return new VariableValueFilter(Objects.requireNonNull(name), eq, neq, gt, gte, lt, lte);
    }
  }
}
