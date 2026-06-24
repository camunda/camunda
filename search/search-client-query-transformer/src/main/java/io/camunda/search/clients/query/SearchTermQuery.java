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

public record SearchTermQuery(String field, TypedValue value, Boolean caseInsensitive)
    implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchTermQuery> {

    private String field;
    private TypedValue value;
    private Boolean caseInsensitive;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder value(final String value) {
      return value(TypedValue.of(value));
    }

    public Builder value(final short value) {
      return value(TypedValue.of(value));
    }

    public Builder value(final int value) {
      return value(TypedValue.of(value));
    }

    public Builder value(final long value) {
      return value(TypedValue.of(value));
    }

    public Builder value(final double value) {
      return value(TypedValue.of(value));
    }

    public Builder value(final boolean value) {
      return value(TypedValue.of(value));
    }

    public Builder value(final TypedValue value) {
      this.value = value;
      return this;
    }

    public Builder caseInsensitive(final Boolean value) {
      caseInsensitive = value;
      return this;
    }

    @Override
    public SearchTermQuery build() {
      return new SearchTermQuery(
          Objects.requireNonNull(field, "Expected non-null field for term query."),
          Objects.requireNonNull(
              value,
              () ->
                  String.format("Expected non-null value for term query, for field '%s'.", field)),
          caseInsensitive);
    }
  }
}
