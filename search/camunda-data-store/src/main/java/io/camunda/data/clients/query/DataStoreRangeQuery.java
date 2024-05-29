/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.query;

import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final record DataStoreRangeQuery(
    String field, Object gt, Object gte, Object lt, Object lte, String from, String to)
    implements DataStoreQueryVariant {

  static DataStoreRangeQuery of(
      final Function<Builder, DataStoreObjectBuilder<DataStoreRangeQuery>> fn) {
    return DataStoreQueryBuilders.range(fn);
  }

  public static final class Builder implements DataStoreObjectBuilder<DataStoreRangeQuery> {

    private String field;
    private Object gt;
    private Object gte;
    private Object lt;
    private Object lte;
    private String from;
    private String to;

    public Builder field(final String field) {
      this.field = field;
      return this;
    }

    public Builder gt(final Object value) {
      gt = value;
      return this;
    }

    public Builder gte(final Object value) {
      gte = value;
      return this;
    }

    public Builder lt(final Object value) {
      lt = lte;
      return this;
    }

    public Builder lte(final Object value) {
      lte = value;
      return this;
    }

    public Builder from(final String value) {
      from = value;
      return this;
    }

    public Builder to(final String value) {
      to = value;
      return this;
    }

    @Override
    public DataStoreRangeQuery build() {
      return new DataStoreRangeQuery(field, gt, gte, lt, lte, from, to);
    }
  }
}
