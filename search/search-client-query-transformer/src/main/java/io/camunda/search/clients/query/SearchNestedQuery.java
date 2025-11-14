/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;

public record SearchNestedQuery(String path, SearchQuery query) implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchNestedQuery> {

    private String path;
    private SearchQuery query;

    public Builder path(final String value) {
      path = value;
      return this;
    }

    public Builder query(final SearchQuery value) {
      query = value;
      return this;
    }

    @Override
    public SearchNestedQuery build() {
      return new SearchNestedQuery(
          Objects.requireNonNull(
              path, "Expected a non-null path parameter for the nested query."),
          Objects.requireNonNull(
              query,
              () ->
                  String.format(
                      "Expected a non-null query parameter for the nested query, with path: '%s'.",
                      path)));
    }
  }
}
