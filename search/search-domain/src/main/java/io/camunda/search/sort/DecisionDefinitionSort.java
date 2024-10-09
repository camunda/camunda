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

public record DecisionDefinitionSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static DecisionDefinitionSort of(
      final Function<Builder, ObjectBuilder<DecisionDefinitionSort>> fn) {
    return SortOptionBuilders.decisionDefinition(fn);
  }

  public static final class Builder extends SortOption.AbstractBuilder<Builder>
      implements ObjectBuilder<DecisionDefinitionSort> {

    public Builder decisionDefinitionKey() {
      currentOrdering = new FieldSorting("key", null);
      return this;
    }

    public Builder decisionDefinitionId() {
      currentOrdering = new FieldSorting("decisionId", null);
      return this;
    }

    public Builder name() {
      currentOrdering = new FieldSorting("name", null);
      return this;
    }

    public Builder version() {
      currentOrdering = new FieldSorting("version", null);
      return this;
    }

    public Builder decisionRequirementsId() {
      currentOrdering = new FieldSorting("decisionRequirementsId", null);
      return this;
    }

    public Builder decisionRequirementsKey() {
      currentOrdering = new FieldSorting("decisionRequirementsKey", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DecisionDefinitionSort build() {
      return new DecisionDefinitionSort(orderings);
    }
  }
}
