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

public record MessageSubscriptionProcessDefinitionStatisticsSort(List<FieldSorting> orderings)
    implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static MessageSubscriptionProcessDefinitionStatisticsSort of(
      final Function<
              MessageSubscriptionProcessDefinitionStatisticsSort.Builder,
              ObjectBuilder<MessageSubscriptionProcessDefinitionStatisticsSort>>
          fn) {
    return SortOptionBuilders.messageSubscriptionProcessDefinitionStatistics(fn);
  }

  public static final class Builder
      extends SortOption.AbstractBuilder<MessageSubscriptionProcessDefinitionStatisticsSort.Builder>
      implements ObjectBuilder<MessageSubscriptionProcessDefinitionStatisticsSort> {

    public Builder processDefinitionKey() {
      currentOrdering = new FieldSorting("processDefinitionKey", null);
      return this;
    }

    public Builder processDefinitionId() {
      currentOrdering = new FieldSorting("processDefinitionId", null);
      return this;
    }

    @Override
    protected MessageSubscriptionProcessDefinitionStatisticsSort.Builder self() {
      return this;
    }

    @Override
    public MessageSubscriptionProcessDefinitionStatisticsSort build() {
      return new MessageSubscriptionProcessDefinitionStatisticsSort(orderings);
    }
  }
}
