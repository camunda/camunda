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

public record SearchHasChildQuery(SearchQuery query, String type) implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchHasChildQuery> {

    private SearchQuery query;
    private String type;

    public Builder query(final SearchQuery value) {
      query = value;
      return this;
    }

    public Builder type(final String value) {
      type = value;
      return this;
    }

    @Override
    public SearchHasChildQuery build() {
      return new SearchHasChildQuery(
          Objects.requireNonNull(
              query, "Expected a non-null query parameter for the hasChild query."),
          Objects.requireNonNull(
              type,
              () ->
                  String.format(
                      "Expected a non-null type parameter for the hasChild query, with query: '%s'.",
                      query)));
    }
  }
}
