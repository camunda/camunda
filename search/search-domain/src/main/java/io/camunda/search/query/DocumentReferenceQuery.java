/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.DocumentReferenceFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.DocumentReferenceSort;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record DocumentReferenceQuery(
    DocumentReferenceFilter filter, DocumentReferenceSort sort, SearchQueryPage page)
    implements TypedSearchQuery<DocumentReferenceFilter, DocumentReferenceSort> {

  public static DocumentReferenceQuery of(
      final Function<Builder, ObjectBuilder<DocumentReferenceQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder extends SearchQueryBase.AbstractQueryBuilder<Builder>
      implements TypedSearchQueryBuilder<
          DocumentReferenceQuery, Builder, DocumentReferenceFilter, DocumentReferenceSort> {

    private static final DocumentReferenceFilter EMPTY_FILTER =
        FilterBuilders.documentReference().build();
    private static final DocumentReferenceSort EMPTY_SORT =
        SortOptionBuilders.documentReference().build();

    private DocumentReferenceFilter filter;
    private DocumentReferenceSort sort;

    @Override
    public Builder filter(final DocumentReferenceFilter value) {
      filter = value;
      return this;
    }

    @Override
    public Builder sort(final DocumentReferenceSort value) {
      sort = value;
      return this;
    }

    public Builder filter(
        final Function<DocumentReferenceFilter.Builder, ObjectBuilder<DocumentReferenceFilter>>
            fn) {
      return filter(FilterBuilders.documentReference(fn));
    }

    public Builder sort(
        final Function<DocumentReferenceSort.Builder, ObjectBuilder<DocumentReferenceSort>> fn) {
      return sort(SortOptionBuilders.documentReference(fn));
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public DocumentReferenceQuery build() {
      filter = Objects.requireNonNullElse(filter, EMPTY_FILTER);
      sort = Objects.requireNonNullElse(sort, EMPTY_SORT);
      return new DocumentReferenceQuery(filter, sort, page());
    }
  }
}
