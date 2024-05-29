/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import static io.camunda.util.DataStoreCollectionUtil.listAdd;
import static io.camunda.util.DataStoreCollectionUtil.listAddAll;

import io.camunda.data.clients.types.DataStoreFieldValue;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class DataStoreTermsQuery implements DataStoreQueryVariant {

  private final String field;
  private final List<DataStoreFieldValue> values;

  private DataStoreTermsQuery(final Builder builder) {
    field = builder.field;
    values = builder.values;
  }

  public String field() {
    return field;
  }

  public List<DataStoreFieldValue> values() {
    return values;
  }

  public static DataStoreTermsQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreTermsQuery>> fn) {
    return DataStoreQueryBuilders.terms(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreTermsQuery> {

    private String field;
    private List<DataStoreFieldValue> values;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder stringTerms(final List<String> values) {
      this.values =
          listAddAll(this.values, DataStoreFieldValue.of(values, DataStoreFieldValue::of));
      return this;
    }

    public Builder stringTerms(final String value, final String... values) {
      return stringTerms(listAdd(new ArrayList<String>(), value, values));
    }

    public Builder intTerms(final List<Integer> values) {
      this.values =
          listAddAll(this.values, DataStoreFieldValue.of(values, DataStoreFieldValue::of));
      return this;
    }

    public Builder intTerms(final Integer value, final Integer... values) {
      return intTerms(listAdd(new ArrayList<Integer>(), value, values));
    }

    public Builder longTerms(final List<Long> values) {
      this.values =
          listAddAll(this.values, DataStoreFieldValue.of(values, DataStoreFieldValue::of));
      return this;
    }

    public Builder longTerms(final Long value, final Long... values) {
      return longTerms(listAdd(new ArrayList<Long>(), value, values));
    }

    @Override
    public DataStoreTermsQuery build() {
      return new DataStoreTermsQuery(this);
    }
  }
}
