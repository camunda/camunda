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

public final record SearchRangeQuery(
    String field,
    Object gt,
    Object gte,
    Object lt,
    Object lte,
    String from,
    String to,
    String format)
    implements SearchQueryOption {

  static SearchRangeQuery of(final Function<Builder, ObjectBuilder<SearchRangeQuery>> fn) {
    return SearchQueryBuilders.range(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchRangeQuery> {

    private String field;
    private Object gt;
    private Object gte;
    private Object lt;
    private Object lte;
    private String from;
    private String to;
    private String format;

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
      lt = value;
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

    public Builder format(final String value) {
      format = value;
      return this;
    }

    @Override
    public SearchRangeQuery build() {
      return new SearchRangeQuery(
          Objects.requireNonNull(field), gt, gte, lt, lte, from, to, format);
    }
  }
}
