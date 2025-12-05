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

public record ProcessDefinitionMessageSubscriptionStatisticsSort(List<FieldSorting> orderings)
    implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static ProcessDefinitionMessageSubscriptionStatisticsSort of(
      final Function<
              ProcessDefinitionMessageSubscriptionStatisticsSort.Builder,
              ObjectBuilder<ProcessDefinitionMessageSubscriptionStatisticsSort>>
          fn) {
    return SortOptionBuilders.processDefinitionMessageSubscriptionStatistics(fn);
  }

  public static final class Builder
      extends AbstractBuilder<ProcessDefinitionMessageSubscriptionStatisticsSort.Builder>
      implements ObjectBuilder<ProcessDefinitionMessageSubscriptionStatisticsSort> {

    public Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public Builder tenantId() {
      currentOrdering = new FieldSorting("tenantId", null);
      return this;
    }

    @Override
    protected ProcessDefinitionMessageSubscriptionStatisticsSort.Builder self() {
      return this;
    }

    @Override
    public ProcessDefinitionMessageSubscriptionStatisticsSort build() {
      return new ProcessDefinitionMessageSubscriptionStatisticsSort(orderings);
    }
  }
}
