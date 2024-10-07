/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.page;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record SearchQueryPage(
    Integer from, Integer size, Object[] searchAfter, Object[] searchBefore) {

  public static Integer DEFAULT_FROM = 0;
  public static Integer DEFAULT_SIZE = 100;

  public boolean isNextPage() {
    return searchAfter != null || !isPreviousPage();
  }

  public boolean isPreviousPage() {
    return searchBefore != null;
  }

  public Object[] startNextPageAfter() {
    if (isNextPage()) {
      return searchAfter;
    } else if (isPreviousPage()) {
      return searchBefore;
    }
    return null;
  }

  public SearchQueryPage sanitize() {
    var newFrom = (this.from == null) ? DEFAULT_FROM : Math.max( 0, Math.min(SearchQueryPage.DEFAULT_FROM, this.from));
    var newSize = (this.size == null) ? DEFAULT_SIZE : Math.max( 0, Math.min(SearchQueryPage.DEFAULT_SIZE, this.size));

    return new SearchQueryPage.Builder()
        .from(newFrom)
        .size(newSize)
        .searchAfter(this.searchAfter)
        .searchBefore(this.searchBefore)
        .build();
  }

  public static SearchQueryPage of(final Function<Builder, ObjectBuilder<SearchQueryPage>> fn) {
    return SearchQueryPageBuilders.page(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchQueryPage> {

    private Integer from = DEFAULT_FROM;
    private Integer size = DEFAULT_SIZE;
    private Object[] searchAfter;
    private Object[] searchBefore;

    public Builder from(final Integer value) {
      from = value;
      return this;
    }

    public Builder size(final Integer value) {
      size = value;
      return this;
    }

    public Builder searchAfter(final Object[] value) {
      searchAfter = value;
      return this;
    }

    public Builder searchBefore(final Object[] value) {
      searchBefore = value;
      return this;
    }

    @Override
    public SearchQueryPage build() {
      return new SearchQueryPage(from, size, searchAfter, searchBefore);
    }
  }
}
