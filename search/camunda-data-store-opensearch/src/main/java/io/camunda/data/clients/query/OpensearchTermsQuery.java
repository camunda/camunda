/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;

public final class OpensearchTermsQuery extends OpensearchQueryVariant<TermsQuery>
    implements DataStoreTermsQuery {

  public OpensearchTermsQuery(final TermsQuery termsQuery) {
    super(termsQuery);
  }

  public static final class Builder implements DataStoreTermsQuery.Builder {

    private final TermsQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new TermsQuery.Builder();
    }

    private <T> Builder terms(final List<T> values, final Function<T, FieldValue> toFieldValue) {
      final var fieldValues = values.stream().map(toFieldValue).toList();
      final var termsQueryField = TermsQueryField.of(f -> f.value(fieldValues));
      wrappedBuilder.terms(termsQueryField);
      return this;
    }

    private <T> List<T> collectTerms(final T value, T... values) {
      final var terms = new ArrayList<T>();
      terms.add(value);

      if (values != null && values.length > 0) {
        for (T v : values) {
          terms.add(v);
        }
      }
      return terms;
    }

    @Override
    public Builder field(final String value) {
      wrappedBuilder.field(value);
      return this;
    }

    @Override
    public Builder stringTerms(final List<String> values) {
      return terms(values, FieldValue::of);
    }

    @Override
    public Builder stringTerms(final String value, final String... values) {
      return stringTerms(collectTerms(value, values));
    }

    @Override
    public Builder longTerms(final List<Long> values) {
      return terms(values, FieldValue::of);
    }

    @Override
    public Builder longTerms(final Long value, final Long... values) {
      return longTerms(collectTerms(value, values));
    }

    @Override
    public Builder intTerms(final Integer value, final Integer... values) {
      return intTerms(collectTerms(value, values));
    }

    @Override
    public Builder intTerms(final List<Integer> values) {
      return terms(values, FieldValue::of);
    }

    @Override
    public DataStoreTermsQuery build() {
      final var termsQuery = wrappedBuilder.build();
      return new OpensearchTermsQuery(termsQuery);
    }
  }
}
