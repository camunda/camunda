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

  public static final Integer DEFAULT_FROM = 0;
  public static final Integer DEFAULT_SIZE = 100;

  public static final SearchQueryPage DEFAULT = new Builder().build();
  public static final SearchQueryPage NO_ENTITIES_QUERY =
      new SearchQueryPage(0, 0, new Object[] {}, new Object[] {});

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
    return new Builder()
        .from(from)
        .size(size)
        .searchAfter(searchAfter)
        .searchBefore(searchBefore)
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
      final var sanitizedFrom = (from == null) ? DEFAULT_FROM : Math.max(0, from);
      final var sanitizedSize = (size == null) ? DEFAULT_SIZE : Math.max(0, size);
      return new SearchQueryPage(sanitizedFrom, sanitizedSize, searchAfter, searchBefore);
    }
  }
}
