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

public record MappingRuleSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static MappingRuleSort of(final Function<Builder, ObjectBuilder<MappingRuleSort>> fn) {
    return SortOptionBuilders.mappingRule(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<MappingRuleSort> {

    public Builder mappingRuleKey() {
      currentOrdering = new FieldSorting("mappingRuleKey", null);
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

    public Builder mappingRuleId() {
      currentOrdering = new FieldSorting("mappingRuleId", null);
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
    public MappingRuleSort build() {
      return new MappingRuleSort(orderings);
    }
  }
}
