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
    Integer from, Integer size, String after, String before, SearchQueryPageType type) {

  public static final Integer DEFAULT_FROM = 0;
  public static final Integer DEFAULT_SIZE = 100;

  public static final SearchQueryPage DEFAULT = new Builder().build();
  public static final SearchQueryPage NO_ENTITIES_QUERY =
      new SearchQueryPage(0, 0, null, null, SearchQueryPageType.PAGE);

  public boolean isNextPage() {
    return after != null || !isPreviousPage();
  }

  public boolean isPreviousPage() {
    return before != null;
  }

  public String startNextPageAfter() {
    if (isNextPage()) {
      return after;
    } else if (isPreviousPage()) {
      return before;
    }
    return null;
  }

  public static SearchQueryPage of(final Function<Builder, ObjectBuilder<SearchQueryPage>> fn) {
    return SearchQueryPageBuilders.page(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchQueryPage> {

    private Integer from = DEFAULT_FROM;
    private Integer size = DEFAULT_SIZE;
    private String after;
    private String before;
    private SearchQueryPageType type = SearchQueryPageType.PAGE;

    public Builder singleResult() {
      type = SearchQueryPageType.SINGLE_RESULT;
      size = 2;
      return this;
    }

    public Builder unlimited() {
      type = SearchQueryPageType.UNLIMITED;
      return this;
    }

    public Builder from(final Integer value) {
      from = value;
      return this;
    }

    public Builder size(final Integer value) {
      size = value;
      return this;
    }

    public Builder after(final String value) {
      after = value;
      return this;
    }

    public Builder before(final String value) {
      before = value;
      return this;
    }

    @Override
    public SearchQueryPage build() {
      final var sanitizedFrom = (from == null) ? DEFAULT_FROM : Math.max(0, from);
      final var sanitizedSize = (size == null) ? DEFAULT_SIZE : Math.max(0, size);
      return new SearchQueryPage(sanitizedFrom, sanitizedSize, after, before, type);
    }
  }

  public enum SearchQueryPageType {
    SINGLE_RESULT,
    UNLIMITED,
    PAGE
  }
}
