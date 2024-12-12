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

public record UsageMetricsSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static UsageMetricsSort of(final Function<Builder, ObjectBuilder<UsageMetricsSort>> fn) {
    return SortOptionBuilders.usageMetrics(fn);
  }

  public static final class Builder extends SortOption.AbstractBuilder<Builder>
      implements ObjectBuilder<UsageMetricsSort> {

    public Builder id() {
      currentOrdering = new FieldSorting("id", null);
      return this;
    }

    public Builder event() {
      currentOrdering = new FieldSorting("event", null);
      return this;
    }

    public Builder eventTime() {
      currentOrdering = new FieldSorting("eventTime", null);
      return this;
    }

    public Builder value() {
      currentOrdering = new FieldSorting("value", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public UsageMetricsSort build() {
      return new UsageMetricsSort(orderings);
    }
  }
}
