/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import static io.camunda.search.clients.core.RequestBuilders.indexRequest;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record SearchIndexRequest<T>(String id, String index, String routing, T document) {

  public static <T> SearchIndexRequest<T> of(
      final Function<SearchIndexRequest.Builder<T>, ObjectBuilder<SearchIndexRequest<T>>> fn) {
    return indexRequest(fn);
  }

  public static final class Builder<T> implements ObjectBuilder<SearchIndexRequest<T>> {

    private String id;
    private String index;
    private String routing;
    private T document;

    public Builder<T> id(final String value) {
      id = value;
      return this;
    }

    public Builder<T> index(final String value) {
      index = value;
      return this;
    }

    public Builder<T> routing(final String value) {
      routing = value;
      return this;
    }

    public Builder<T> document(final T value) {
      document = value;
      return this;
    }

    @Override
    public SearchIndexRequest<T> build() {
      return new SearchIndexRequest<T>(
          id,
          Objects.requireNonNull(
              index, "Expected to create request for index, but given index was null."),
          routing,
          Objects.requireNonNull(
              document, "Expected to index a document, but given document was null."));
    }
  }
}
