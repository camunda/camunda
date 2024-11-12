/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public final record VariableSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static VariableSort of(final Function<Builder, ObjectBuilder<VariableSort>> fn) {
    return SortOptionBuilders.variable(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<VariableSort> {

    public Builder value() {
      currentOrdering = new FieldSorting("value", null);
      return this;
    }

    public Builder name() {
      currentOrdering = new FieldSorting("name", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    public Builder variableKey() {
      currentOrdering = new FieldSorting("key", null);
      return this;
    }

    public Builder scopeKey() {
      currentOrdering = new FieldSorting("scopeKey", null);
      return this;
    }

    public Builder processInstanceKey() {
      currentOrdering = new FieldSorting("processInstanceKey", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public VariableSort build() {
      return new VariableSort(orderings);
    }
  }
}
