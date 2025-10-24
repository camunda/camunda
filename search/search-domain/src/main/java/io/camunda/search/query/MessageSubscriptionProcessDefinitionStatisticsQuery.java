/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.aggregation.MessageSubscriptionProcessDefinitionStatisticsAggregation;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MessageSubscriptionProcessDefinitionStatisticsFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.MessageSubscriptionProcessDefinitionStatisticsSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record MessageSubscriptionProcessDefinitionStatisticsQuery(
    MessageSubscriptionProcessDefinitionStatisticsFilter filter,
    MessageSubscriptionProcessDefinitionStatisticsSort sort,
    SearchQueryPage page)
    implements TypedSearchAggregationQuery<
        MessageSubscriptionProcessDefinitionStatisticsFilter,
        MessageSubscriptionProcessDefinitionStatisticsSort,
        MessageSubscriptionProcessDefinitionStatisticsAggregation> {

  @Override
  public MessageSubscriptionProcessDefinitionStatisticsAggregation aggregation() {
    return new MessageSubscriptionProcessDefinitionStatisticsAggregation(filter, sort, page);
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<
          MessageSubscriptionProcessDefinitionStatisticsQuery.Builder>
      implements TypedSearchQueryBuilder<
          MessageSubscriptionProcessDefinitionStatisticsQuery,
          MessageSubscriptionProcessDefinitionStatisticsQuery.Builder,
          MessageSubscriptionProcessDefinitionStatisticsFilter,
          MessageSubscriptionProcessDefinitionStatisticsSort> {

    private static final MessageSubscriptionProcessDefinitionStatisticsFilter EMPTY_FILTER =
        FilterBuilders.messageSubscriptionProcessDefinitionStatistics().build();
    private static final MessageSubscriptionProcessDefinitionStatisticsSort EMPTY_SORT =
        SortOptionBuilders.messageSubscriptionProcessDefinitionStatistics().build();

    private MessageSubscriptionProcessDefinitionStatisticsFilter filter;
    private MessageSubscriptionProcessDefinitionStatisticsSort sort;

    @Override
    public MessageSubscriptionProcessDefinitionStatisticsQuery.Builder filter(
        final MessageSubscriptionProcessDefinitionStatisticsFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final MessageSubscriptionProcessDefinitionStatisticsSort value) {
      sort = value;
      return this;
    }

    public MessageSubscriptionProcessDefinitionStatisticsQuery.Builder filter(
        final Function<
                MessageSubscriptionProcessDefinitionStatisticsFilter.Builder,
                ObjectBuilder<MessageSubscriptionProcessDefinitionStatisticsFilter>>
            fn) {
      return filter(FilterBuilders.messageSubscriptionProcessDefinitionStatistics(fn));
    }

    public MessageSubscriptionProcessDefinitionStatisticsQuery.Builder sort(
        final Function<
                MessageSubscriptionProcessDefinitionStatisticsSort.Builder,
                ObjectBuilder<MessageSubscriptionProcessDefinitionStatisticsSort>>
            fn) {
      return sort(SortOptionBuilders.messageSubscriptionProcessDefinitionStatistics(fn));
    }

    @Override
    protected MessageSubscriptionProcessDefinitionStatisticsQuery.Builder self() {
      return this;
    }

    @Override
    public MessageSubscriptionProcessDefinitionStatisticsQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new MessageSubscriptionProcessDefinitionStatisticsQuery(filter, sort, page());
    }
  }
}
