/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import static io.camunda.util.CollectionUtil.addValuesToList;

import io.camunda.search.clients.types.TypedValue;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record SearchTermsQuery(String field, List<TypedValue> values) implements SearchQueryOption {

  public static SearchTermsQuery of(final Function<Builder, ObjectBuilder<SearchTermsQuery>> fn) {
    return SearchQueryBuilders.terms(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchTermsQuery> {

    private String field;
    private List<TypedValue> terms;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder stringTerms(final List<String> values) {
      return terms(TypedValue.of(values, TypedValue::of));
    }

    public Builder intTerms(final List<Integer> values) {
      return terms(TypedValue.of(values, TypedValue::of));
    }

    public Builder longTerms(final List<Long> values) {
      return terms(TypedValue.of(values, TypedValue::of));
    }

    private Builder terms(final List<TypedValue> values) {
      terms = addValuesToList(terms, values);
      return this;
    }

    @Override
    public SearchTermsQuery build() {
      return new SearchTermsQuery(Objects.requireNonNull(field), Objects.requireNonNull(terms));
    }
  }
}
