/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.SequenceFlowFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.NoSort;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record SequenceFlowQuery(SequenceFlowFilter filter)
    implements TypedSearchQuery<SequenceFlowFilter, NoSort> {
  public static SequenceFlowQuery of(final Function<Builder, ObjectBuilder<SequenceFlowQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  @Override
  public NoSort sort() {
    return NoSort.NO_SORT;
  }

  @Override
  public List<SearchSortOptions> retainValidSortings(final List<SearchSortOptions> sorting) {
    final var fieldNames = List.of("ownerId", "ownerType", "resourceIds", "resourceType");
    return sorting.stream().filter(s -> fieldNames.contains(s.field().field())).toList();
  }

  @Override
  public SearchQueryPage page() {
    return SearchQueryPage.DEFAULT;
  }

  public static final class Builder extends AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          SequenceFlowQuery, SequenceFlowQuery.Builder, SequenceFlowFilter, NoSort> {
    private static final SequenceFlowFilter EMPTY_FILTER = FilterBuilders.sequenceFlow().build();

    private SequenceFlowFilter filter;

    @Override
    public Builder filter(final SequenceFlowFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final NoSort value) {
      return this;
    }

    public Builder filter(
        final Function<SequenceFlowFilter.Builder, ObjectBuilder<SequenceFlowFilter>> fn) {
      return filter(FilterBuilders.sequenceFlow(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public SequenceFlowQuery build() {
      return new SequenceFlowQuery(Objects.requireNonNullElse(filter, EMPTY_FILTER));
    }
  }
}
