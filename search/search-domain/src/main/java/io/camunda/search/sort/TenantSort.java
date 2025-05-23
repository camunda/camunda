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

public record TenantSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static TenantSort of(final Function<Builder, ObjectBuilder<TenantSort>> fn) {
    return SortOptionBuilders.tenant(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<TenantSort> {

    public Builder tenantKey() {
      currentOrdering = new FieldSorting("key", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    public Builder name() {
      currentOrdering = new FieldSorting("name", null);
      return this;
    }

    public Builder memberId() {
      currentOrdering = new FieldSorting("memberId", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder asc() {
      return addOrdering(SortOrder.ASC);
    }

    @Override
    public Builder desc() {
      return addOrdering(SortOrder.DESC);
    }

    @Override
    public TenantSort build() {
      return new TenantSort(orderings);
    }
  }
}
