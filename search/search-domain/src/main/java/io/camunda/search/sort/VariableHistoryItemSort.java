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

public record VariableHistoryItemSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static VariableHistoryItemSort of(
      final Function<VariableHistoryItemSort.Builder, ObjectBuilder<VariableHistoryItemSort>> fn) {
    return SortOptionBuilders.variableHistoryItem(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<VariableHistoryItemSort> {

    public Builder variableHistoryItemKey() {
      currentOrdering = new FieldSorting("variableHistoryItemKey", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public VariableHistoryItemSort build() {
      return new VariableHistoryItemSort(orderings);
    }
  }
}
