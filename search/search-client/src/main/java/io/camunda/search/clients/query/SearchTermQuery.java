/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import io.camunda.search.clients.types.TypedValue;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record SearchTermQuery(String field, TypedValue value, Boolean caseInsensitive)
    implements SearchQueryOption {

  static SearchTermQuery of(final Function<Builder, ObjectBuilder<SearchTermQuery>> fn) {
    return SearchQueryBuilders.term(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchTermQuery> {

    private String field;
    private TypedValue value;
    private Boolean caseInsensitive;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder value(final String value) {
      this.value = TypedValue.of(value);
      return this;
    }

    public Builder value(final int value) {
      this.value = TypedValue.of(value);
      return this;
    }

    public Builder value(final long value) {
      this.value = TypedValue.of(value);
      return this;
    }

    public Builder value(final double value) {
      this.value = TypedValue.of(value);
      return this;
    }

    public Builder value(final boolean value) {
      this.value = TypedValue.of(value);
      return this;
    }

    public Builder caseInsensitive(final Boolean value) {
      caseInsensitive = value;
      return this;
    }

    @Override
    public SearchTermQuery build() {
      return new SearchTermQuery(
          Objects.requireNonNull(field), Objects.requireNonNull(value), caseInsensitive);
    }
  }
}
