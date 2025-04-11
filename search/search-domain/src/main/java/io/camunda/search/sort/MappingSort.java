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

public record MappingSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static MappingSort of(final Function<Builder, ObjectBuilder<MappingSort>> fn) {
    return SortOptionBuilders.mapping(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<MappingSort> {

    public Builder mappingKey() {
      currentOrdering = new FieldSorting("mappingKey", null);
      return this;
    }

    public Builder claimName() {
      currentOrdering = new FieldSorting("claimName", null);
      return this;
    }

    public Builder claimValue() {
      currentOrdering = new FieldSorting("claimValue", null);
      return this;
    }

    public Builder name() {
      currentOrdering = new FieldSorting("name", null);
      return this;
    }

    public Builder mappingId() {
      currentOrdering = new FieldSorting("mappingId", null);
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
    public MappingSort build() {
      return new MappingSort(orderings);
    }
  }
}
