/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public class OpensearchTermQuery extends OpensearchQueryVariant<TermQuery>
    implements DataStoreTermQuery {

  public OpensearchTermQuery(final TermQuery termQuery) {
    super(termQuery);
  }

  public static final class Builder implements DataStoreTermQuery.Builder {

    private TermQuery.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new TermQuery.Builder();
    }

    @Override
    public Builder field(final String field) {
      wrappedBuilder.field(field);
      return this;
    }

    @Override
    public Builder value(String value) {
      wrappedBuilder.value(FieldValue.of(value));
      return this;
    }

    @Override
    public Builder value(int value) {
      wrappedBuilder.value(FieldValue.of(value));
      return this;
    }

    @Override
    public Builder value(long value) {
      wrappedBuilder.value(FieldValue.of(value));
      return this;
    }

    @Override
    public Builder value(double value) {
      wrappedBuilder.value(FieldValue.of(value));
      return this;
    }

    @Override
    public Builder value(boolean value) {
      wrappedBuilder.value(FieldValue.of(value));
      return this;
    }

    @Override
    public Builder caseInsensitive(final Boolean value) {
      wrappedBuilder.caseInsensitive(value);
      return this;
    }

    @Override
    public DataStoreTermQuery build() {
      final var fieldValue = wrappedBuilder.build();
      return new OpensearchTermQuery(fieldValue);
    }
  }
}
