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

public record SearchConstantScoreQuery(SearchQuery query) implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchConstantScoreQuery> {

    private SearchQuery filter;

    public Builder filter(final SearchQuery value) {
      filter = value;
      return this;
    }

    @Override
    public SearchConstantScoreQuery build() {
      return new SearchConstantScoreQuery(
          Objects.requireNonNull(
              filter, "Expected a non-null filter for the constant score query."));
    }
  }
}
