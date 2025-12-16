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

public record IncidentProcessInstanceStatisticsSort(List<FieldSorting> orderings)
    implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static IncidentProcessInstanceStatisticsSort of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsSort>> fn) {
    return SortOptionBuilders.incidentProcessInstanceStatistics(fn);
  }

  public static final class Builder
      extends SortOption.AbstractBuilder<IncidentProcessInstanceStatisticsSort.Builder>
      implements ObjectBuilder<IncidentProcessInstanceStatisticsSort> {

    public Builder errorHashCode() {
      currentOrdering = new FieldSorting("errorHashCode", null);
      return this;
    }

    public Builder errorMessage() {
      currentOrdering = new FieldSorting("errorMessage", null);
      return this;
    }

    public Builder activeInstancesWithErrorCount() {
      currentOrdering = new FieldSorting("activeInstancesWithErrorCount", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public IncidentProcessInstanceStatisticsSort build() {
      return new IncidentProcessInstanceStatisticsSort(orderings);
    }
  }
}
