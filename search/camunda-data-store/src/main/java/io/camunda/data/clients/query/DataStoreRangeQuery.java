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

public final class DataStoreRangeQuery implements DataStoreQueryVariant {

  private final String field;
  private final Object gt;
  private final Object gte;
  private final Object lt;
  private final Object lte;
  private final String from;
  private final String to;

  private DataStoreRangeQuery(final Builder builder) {
    field = builder.field;
    gt = builder.gt;
    gte = builder.gte;
    lt = builder.lt;
    lte = builder.lte;
    from = builder.from;
    to = builder.to;
  }

  public String field() {
    return field;
  }

  public Object gt() {
    return gt;
  }

  public Object gte() {
    return gte;
  }

  public Object lt() {
    return lt;
  }

  public Object lte() {
    return lte;
  }

  public String from() {
    return from;
  }

  public String to() {
    return to;
  }

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
      return new DataStoreRangeQuery(this);
    }
  }
}
