/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static io.camunda.data.clients.query.DataStoreQueryBuilders.and;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.range;
import static io.camunda.data.clients.query.DataStoreQueryBuilders.term;

import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.util.DataStoreObjectBuilder;

public record VariableValueFilter(
    String name, Object eq, Object gt, Object gte, Object lt, Object lte) implements FilterBase {

  @Override
  public DataStoreQuery toSearchQuery() {
    DataStoreQuery variableNameQuery = null;
    DataStoreQuery variableValueQuery = null;

    if (name != null) {
      variableNameQuery = term("varName", name);
    }

    final var builder = range().field("varValue");

    if (eq != null) {
      // TODO: change to terms query
      builder.gte(eq).lte(eq);
    }

    if (gt != null) {
      builder.gt(gt);
    }

    if (gte != null) {
      builder.gte(gte);
    }

    if (lt != null) {
      builder.lt(lt);
    }

    if (lte != null) {
      builder.lte(lte);
    }

    variableValueQuery = builder.build().toQuery();

    return and(variableNameQuery, variableValueQuery);
  }

  public static final class Builder implements DataStoreObjectBuilder<VariableValueFilter> {

    private String name;
    private Object eq;
    private Object gt;
    private Object gte;
    private Object lt;
    private Object lte;

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder eq(final Object value) {
      eq = value;
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
      return new VariableValueFilter(name, eq, gt, gte, lt, lte);
    }
  }
}
