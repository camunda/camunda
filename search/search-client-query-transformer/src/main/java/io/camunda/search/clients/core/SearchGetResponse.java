/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import io.camunda.search.clients.core.SearchQueryResponse.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record SearchGetResponse<T>(String id, String index, boolean found, T source) {

  public static <T> SearchGetResponse<T> of(
      final Function<SearchGetResponse.Builder<T>, ObjectBuilder<SearchGetResponse<T>>> fn) {
    return fn.apply(new SearchGetResponse.Builder<T>()).build();
  }

  public static final class Builder<T> implements ObjectBuilder<SearchGetResponse<T>> {

    private String id;
    private String index;
    private Boolean found;
    private T source;

    public Builder<T> id(final String value) {
      id = value;
      return this;
    }

    public Builder<T> index(final String value) {
      index = value;
      return this;
    }

    public Builder<T> found(final boolean value) {
      found = value;
      return this;
    }

    public Builder<T> source(final T value) {
      source = value;
      return this;
    }

    @Override
    public SearchGetResponse<T> build() {
      return new SearchGetResponse<T>(
          Objects.requireNonNull(id),
          Objects.requireNonNull(index),
          Objects.requireNonNull(found),
          source);
    }
  }
}
