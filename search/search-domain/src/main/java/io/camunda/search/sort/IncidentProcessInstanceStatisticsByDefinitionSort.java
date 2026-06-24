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

public record IncidentProcessInstanceStatisticsByDefinitionSort(List<FieldSorting> orderings)
    implements SortOption {
  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static IncidentProcessInstanceStatisticsByDefinitionSort of(
      final Function<Builder, ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionSort>>
          fn) {
    return SortOptionBuilders.incidentProcessInstanceStatisticsByDefinition(fn);
  }

  public static final class Builder
      extends SortOption.AbstractBuilder<IncidentProcessInstanceStatisticsByDefinitionSort.Builder>
      implements io.camunda.util.ObjectBuilder<IncidentProcessInstanceStatisticsByDefinitionSort> {

    public Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
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
    public IncidentProcessInstanceStatisticsByDefinitionSort build() {
      return new IncidentProcessInstanceStatisticsByDefinitionSort(orderings);
    }
  }
}
