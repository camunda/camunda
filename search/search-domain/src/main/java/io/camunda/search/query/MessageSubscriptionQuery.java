/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.MessageSubscriptionSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record MessageSubscriptionQuery(
    MessageSubscriptionFilter filter, MessageSubscriptionSort sort, SearchQueryPage page)
    implements TypedSearchQuery<MessageSubscriptionFilter, MessageSubscriptionSort> {

  public static MessageSubscriptionQuery of(
      final Function<MessageSubscriptionQuery.Builder, ObjectBuilder<MessageSubscriptionQuery>>
          fn) {
    return fn.apply(new MessageSubscriptionQuery.Builder()).build();
  }

  public static final class Builder
      extends SearchQueryBase.AbstractQueryBuilder<MessageSubscriptionQuery.Builder>
      implements TypedSearchQueryBuilder<
          MessageSubscriptionQuery,
          MessageSubscriptionQuery.Builder,
          MessageSubscriptionFilter,
          MessageSubscriptionSort> {

    private static final MessageSubscriptionFilter EMPTY_FILTER =
        FilterBuilders.messageSubscription().build();
    private static final MessageSubscriptionSort EMPTY_SORT =
        SortOptionBuilders.messageSubscription().build();

    private MessageSubscriptionFilter filter;
    private MessageSubscriptionSort sort;

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder filter(final MessageSubscriptionFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final MessageSubscriptionSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>
            fn) {
      return filter(FilterBuilders.messageSubscription(fn));
    }

    public Builder sort(
        final Function<MessageSubscriptionSort.Builder, ObjectBuilder<MessageSubscriptionSort>>
            fn) {
      return sort(SortOptionBuilders.messageSubscription(fn));
    }

    @Override
    public MessageSubscriptionQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new MessageSubscriptionQuery(filter, sort, page());
    }
  }
}
