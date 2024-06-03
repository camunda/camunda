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

public final record SearchMatchQuery(String field, String query, String operator)
    implements SearchQueryOption {

  static SearchMatchQuery of(final Function<Builder, ObjectBuilder<SearchMatchQuery>> fn) {
    return SearchQueryBuilders.match(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchMatchQuery> {

    private String field;
    private String query;
    private String operator;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder query(final String value) {
      query = value;
      return this;
    }

    public Builder operator(final String value) {
      operator = value;
      return this;
    }

    @Override
    public SearchMatchQuery build() {
      return new SearchMatchQuery(
          Objects.requireNonNull(field), Objects.requireNonNull(query), operator);
    }
  }
}
