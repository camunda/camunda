/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record SearchFieldSort(String field, SortOrder order, String missing) {

  public boolean asc() {
    return SortOrder.ASC == order;
  }

  public boolean desc() {
    return SortOrder.DESC == order;
  }

  public static SearchFieldSort of(final Function<Builder, ObjectBuilder<SearchFieldSort>> fn) {
    return SortOptionsBuilders.field(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchFieldSort> {

    private String field;
    private SortOrder order;
    private String missing;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder asc() {
      return order(SortOrder.ASC);
    }

    public Builder desc() {
      return order(SortOrder.DESC);
    }

    public Builder order(final SortOrder order) {
      this.order = order;
      return this;
    }

    public Builder missing(final String value) {
      missing = value;
      return this;
    }

    @Override
    public SearchFieldSort build() {
      return new SearchFieldSort(
          Objects.requireNonNull(field, "Expected field name for field sorting, but got null."),
          order,
          missing);
    }
  }
}
