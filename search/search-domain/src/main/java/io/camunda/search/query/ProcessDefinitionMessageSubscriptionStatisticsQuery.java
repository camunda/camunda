/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.ProcessDefinitionMessageSubscriptionStatisticsAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record ProcessDefinitionMessageSubscriptionStatisticsQuery(
    MessageSubscriptionFilter filter, SearchQueryPage page)
    implements TypedSearchAggregationQuery<
        MessageSubscriptionFilter,
        NoSort,
        ProcessDefinitionMessageSubscriptionStatisticsAggregation> {

  @Override
  public NoSort sort() {
    return NoSort.NO_SORT;
  }

  @Override
  public ProcessDefinitionMessageSubscriptionStatisticsAggregation aggregation() {
    return new ProcessDefinitionMessageSubscriptionStatisticsAggregation(page);
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<
          ProcessDefinitionMessageSubscriptionStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          ProcessDefinitionMessageSubscriptionStatisticsQuery,
          ProcessDefinitionMessageSubscriptionStatisticsQuery.Builder,
          MessageSubscriptionFilter,
          NoSort> {

    private static final MessageSubscriptionFilter EMPTY_FILTER =
        FilterBuilders.messageSubscription().build();

    private MessageSubscriptionFilter filter;

    @Override
    protected ProcessDefinitionMessageSubscriptionStatisticsQuery.Builder self() {
      return this;
    }

    @Override
    public ProcessDefinitionMessageSubscriptionStatisticsQuery.Builder filter(
        final MessageSubscriptionFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public ProcessDefinitionMessageSubscriptionStatisticsQuery.Builder filter(
        final Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>
            fn) {
      return filter(FilterBuilders.messageSubscription(fn));
    }

    @Override
    public ProcessDefinitionMessageSubscriptionStatisticsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      return new ProcessDefinitionMessageSubscriptionStatisticsQuery(filter, page());
    }
  }
}
