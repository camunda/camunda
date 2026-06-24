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

public record AgentInstanceHistorySort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static AgentInstanceHistorySort of(
      final Function<AgentInstanceHistorySort.Builder, ObjectBuilder<AgentInstanceHistorySort>>
          fn) {
    return SortOptionBuilders.agentInstanceHistory(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<AgentInstanceHistorySort> {

    public Builder historyItemKey() {
      currentOrdering = new FieldSorting("historyItemKey", null);
      return this;
    }

    public Builder iteration() {
      currentOrdering = new FieldSorting("iteration", null);
      return this;
    }

    public Builder producedAt() {
      currentOrdering = new FieldSorting("producedAt", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public AgentInstanceHistorySort build() {
      return new AgentInstanceHistorySort(orderings);
    }
  }
}
