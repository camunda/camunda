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

public record SearchHasParentQuery(SearchQuery query, String parentType)
    implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchHasParentQuery> {

    private SearchQuery query;
    private String parentType;

    public Builder query(final SearchQuery value) {
      query = value;
      return this;
    }

    public Builder parentType(final String value) {
      parentType = value;
      return this;
    }

    @Override
    public SearchHasParentQuery build() {
      return new SearchHasParentQuery(
          Objects.requireNonNull(
              query, "Expected a non-null query parameter for the hasParent query."),
          Objects.requireNonNull(
              parentType,
              () ->
                  String.format(
                      "Expected a non-null parentType parameter for the hasParent query, with query: '%s'.",
                      query)));
    }
  }
}
