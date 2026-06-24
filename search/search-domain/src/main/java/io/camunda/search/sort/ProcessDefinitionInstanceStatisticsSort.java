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

public record ProcessDefinitionInstanceStatisticsSort(List<FieldSorting> orderings)
    implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static ProcessDefinitionInstanceStatisticsSort of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionInstanceStatisticsSort>> fn) {
    return SortOptionBuilders.processDefinitionInstanceStatistics(fn);
  }

  public static final class Builder
      extends SortOption.AbstractBuilder<ProcessDefinitionInstanceStatisticsSort.Builder>
      implements ObjectBuilder<ProcessDefinitionInstanceStatisticsSort> {

    public Builder processDefinitionId() {
      currentOrdering = new FieldSorting("processDefinitionId", null);
      return this;
    }

    public Builder activeInstancesWithIncidentCount() {
      currentOrdering = new FieldSorting("activeInstancesWithIncidentCount", null);
      return this;
    }

    public Builder activeInstancesWithoutIncidentCount() {
      currentOrdering = new FieldSorting("activeInstancesWithoutIncidentCount", null);
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcessDefinitionInstanceStatisticsSort build() {
      return new ProcessDefinitionInstanceStatisticsSort(orderings);
    }
  }
}
