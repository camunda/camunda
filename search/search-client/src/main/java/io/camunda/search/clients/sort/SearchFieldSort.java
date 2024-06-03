/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.sort;

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
      order = SortOrder.ASC;
      return this;
    }

    public Builder desc() {
      order = SortOrder.DESC;
      return this;
    }

    public Builder order(SortOrder order) {
      if (order == SortOrder.ASC) {
        return asc();
      } else if (order == SortOrder.DESC) {
        return desc();
      }
      throw new RuntimeException("something went wrong");
    }

    public Builder missing(String value) {
      missing = value;
      return this;
    }

    @Override
    public SearchFieldSort build() {
      return new SearchFieldSort(Objects.requireNonNull(field), order, missing);
    }
  }
}
