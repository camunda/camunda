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
import java.util.function.Function;

public final record SearchHasChildQuery(SearchQuery query, String type)
    implements SearchQueryOption {

  static SearchHasChildQuery of(final Function<Builder, ObjectBuilder<SearchHasChildQuery>> fn) {
    return SearchQueryBuilders.hasChild(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchHasChildQuery> {

    private SearchQuery query;
    private String type;

    public Builder query(final SearchQuery value) {
      query = value;
      return this;
    }

    public Builder query(final Function<SearchQuery.Builder, ObjectBuilder<SearchQuery>> fn) {
      return query(SearchQueryBuilders.query(fn));
    }

    public Builder type(final String value) {
      type = value;
      return this;
    }

    @Override
    public SearchHasChildQuery build() {
      return new SearchHasChildQuery(Objects.requireNonNull(query), Objects.requireNonNull(type));
    }
  }
}
