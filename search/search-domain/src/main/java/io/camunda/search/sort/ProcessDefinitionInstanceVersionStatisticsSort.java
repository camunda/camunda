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

public record ProcessDefinitionInstanceVersionStatisticsSort(List<FieldSorting> orderings)
    implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static ProcessDefinitionInstanceVersionStatisticsSort of(
      final Function<Builder, ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsSort>> fn) {
    return SortOptionBuilders.processDefinitionInstanceVersionStatistics(fn);
  }

  public static final class Builder
      extends SortOption.AbstractBuilder<ProcessDefinitionInstanceVersionStatisticsSort.Builder>
      implements ObjectBuilder<ProcessDefinitionInstanceVersionStatisticsSort> {

    public Builder processDefinitionId() {
      currentOrdering = new FieldSorting("processDefinitionId", null);
      return this;
    }

    public Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public Builder processDefinitionName() {
      currentOrdering = new FieldSorting("processDefinitionName", null);
      return this;
    }

    public Builder processDefinitionVersion() {
      currentOrdering = new FieldSorting("processDefinitionVersion", null);
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
    public ProcessDefinitionInstanceVersionStatisticsSort build() {
      return new ProcessDefinitionInstanceVersionStatisticsSort(orderings);
    }
  }
}
