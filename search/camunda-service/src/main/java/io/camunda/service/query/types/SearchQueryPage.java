/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.types;

import static io.camunda.data.clients.types.DataStoreSortOptionsBuilders.sortOptions;

import io.camunda.data.clients.types.DataStoreSortOptions;
import io.camunda.data.clients.types.SortOrder;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public class SearchQueryPage {

  private final Integer from;
  private final Integer size;
  private final Object[] searchAfter;
  private final Object[] searchAfterOrEqual;
  private final Object[] searchBefore;
  private final Object[] searchBeforeOrEqual;

  public SearchQueryPage(final Builder builder) {
    from = builder.from;
    size = builder.size;
    searchAfter = builder.searchAfter;
    searchAfterOrEqual = builder.searchAfterOrEqual;
    searchBefore = builder.searchBefore;
    searchBeforeOrEqual = builder.searchBeforeOrEqual;
  }

  public Integer from() {
    return from;
  }

  public Integer size() {
    return size;
  }

  public Object[] searchAfter() {
    return searchAfter;
  }

  public Object[] searchAfterOrEqual() {
    return searchAfterOrEqual;
  }

  public Object[] searchBefore() {
    return searchBefore;
  }

  public Object[] searchBeforeOrEqual() {
    return searchBeforeOrEqual;
  }

  public boolean isNextPage() {
    return searchAfter != null || searchAfterOrEqual != null || !isPreviousPage();
  }

  public boolean isPreviousPage() {
    return searchBefore != null || searchBeforeOrEqual != null;
  }

  // TODO: rename it to something more meaningful
  public Object[] getSearchAfter() {
    if (searchAfter != null) {
      return searchAfter;
    } else if (searchAfterOrEqual != null) {
      return searchAfterOrEqual;
    } else if (searchBefore != null) {
      return searchBefore;
    } else if (searchBeforeOrEqual != null) {
      return searchBeforeOrEqual;
    }
    return null;
  }

  public DataStoreSortOptions toSort() {
    if (isNextPage()) {
      return sortOptions("key", SortOrder.ASC);
    } else {
      return sortOptions("key", SortOrder.DESC);
    }
  }

  public static SearchQueryPage of(
      final Function<Builder, DataStoreObjectBuilder<SearchQueryPage>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements DataStoreObjectBuilder<SearchQueryPage> {

    private Integer from;
    private Integer size;
    private Object[] searchAfter;
    private Object[] searchAfterOrEqual;
    private Object[] searchBefore;
    private Object[] searchBeforeOrEqual;

    public Builder from(final Integer from) {
      this.from = from;
      return this;
    }

    public Builder size(final Integer size) {
      this.size = size;
      return this;
    }

    public Builder searchAfter(final Object[] searchAfter) {
      this.searchAfter = searchAfter;
      return this;
    }

    public Builder searchAfterOrEqual(final Object[] searchAfterOrEqual) {
      this.searchAfterOrEqual = searchAfterOrEqual;
      return this;
    }

    public Builder searchBefore(final Object[] searchBefore) {
      this.searchBefore = searchBefore;
      return this;
    }

    public Builder searchBeforeOrEqual(final Object[] searchBeforeOrEqual) {
      this.searchBeforeOrEqual = searchBeforeOrEqual;
      return this;
    }

    @Override
    public SearchQueryPage build() {
      return new SearchQueryPage(this);
    }
  }
}
