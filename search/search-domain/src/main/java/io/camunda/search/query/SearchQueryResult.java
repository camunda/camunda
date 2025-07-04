/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record SearchQueryResult<T>(
    long total, List<T> items, String startCursor, String endCursor) {

  public T singleResult()

  public static <T> SearchQueryResult<T> empty() {
    return new SearchQueryResult<>(0, Collections.emptyList(), null, null);
  }

  public static final class Builder<T> implements ObjectBuilder<SearchQueryResult<T>> {

    private long total;
    private List<T> items;
    private String startCursor;
    private String endCursor;

    public Builder<T> total(final long value) {
      total = value;
      return this;
    }

    public Builder<T> items(final List<T> values) {
      items = values;
      return this;
    }

    public Builder<T> startCursor(final String values) {
      startCursor = values;
      return this;
    }

    public Builder<T> endCursor(final String values) {
      endCursor = values;
      return this;
    }

    @Override
    public SearchQueryResult<T> build() {
      return new SearchQueryResult<T>(
          total,
          Objects.requireNonNullElse(items, Collections.emptyList()),
          startCursor,
          endCursor);
    }
  }
}
