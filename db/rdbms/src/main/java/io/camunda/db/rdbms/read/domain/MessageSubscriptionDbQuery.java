/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record MessageSubscriptionDbQuery(
    MessageSubscriptionFilter filter,
    DbQuerySorting<MessageSubscriptionEntity> sort,
    DbQueryPage page) {
  public static MessageSubscriptionDbQuery of(
      final Function<Builder, ObjectBuilder<MessageSubscriptionDbQuery>> fn) {
    return fn.apply(new MessageSubscriptionDbQuery.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<MessageSubscriptionDbQuery> {
    private static final MessageSubscriptionFilter EMPTY_FILTER =
        FilterBuilders.messageSubscription().build();

    private MessageSubscriptionFilter filter;
    private DbQuerySorting<MessageSubscriptionEntity> sort;
    private DbQueryPage page;

    public Builder filter(final MessageSubscriptionFilter value) {
      filter = value;
      return this;
    }

    public Builder sort(final DbQuerySorting<MessageSubscriptionEntity> value) {
      sort = value;
      return this;
    }

    public Builder page(final DbQueryPage value) {
      page = value;
      return this;
    }

    public Builder filter(
        final Function<MessageSubscriptionFilter.Builder, ObjectBuilder<MessageSubscriptionFilter>>
            fn) {
      return filter(FilterBuilders.messageSubscription(fn));
    }

    public Builder sort(
        final Function<
                DbQuerySorting.Builder<MessageSubscriptionEntity>,
                ObjectBuilder<DbQuerySorting<MessageSubscriptionEntity>>>
            fn) {
      return sort(DbQuerySorting.of(fn));
    }

    @Override
    public MessageSubscriptionDbQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, new DbQuerySorting<>(List.of()));
      return new MessageSubscriptionDbQuery(filter, sort, page);
    }
  }
}
