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

public final record SearchExistsQuery(String field) implements SearchQueryOption {

  static SearchExistsQuery of(final Function<Builder, ObjectBuilder<SearchExistsQuery>> fn) {
    return SearchQueryBuilders.exists(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchExistsQuery> {

    private String field;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    @Override
    public SearchExistsQuery build() {
      return new SearchExistsQuery(Objects.requireNonNull(field));
    }
  }
}
