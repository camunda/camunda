/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.types;

import co.elastic.clients.elasticsearch._types.FieldSort;

public class ElasticsearchFieldSort implements DataStoreFieldSort {

  private final FieldSort wrappedFieldSort;

  public ElasticsearchFieldSort(final FieldSort fieldSort) {
    wrappedFieldSort = fieldSort;
  }

  @Override
  public String field() {
    return wrappedFieldSort.field();
  }

  @Override
  public SortOrder order() {
    return SortOrder.valueOf(wrappedFieldSort.order().jsonValue());
  }

  public FieldSort fieldSort() {
    return wrappedFieldSort;
  }

  public static final class Builder implements DataStoreFieldSort.Builder {

    private final FieldSort.Builder wrappedBuilder;

    public Builder() {
      wrappedBuilder = new FieldSort.Builder();
    }

    @Override
    public Builder field(final String field) {
      wrappedBuilder.field(field);
      return this;
    }

    @Override
    public Builder asc() {
      wrappedBuilder.order(co.elastic.clients.elasticsearch._types.SortOrder.Asc);
      return this;
    }

    @Override
    public Builder desc() {
      wrappedBuilder.order(co.elastic.clients.elasticsearch._types.SortOrder.Desc);
      return this;
    }

    @Override
    public Builder order(SortOrder order) {
      if (order == SortOrder.ASC) {
        return asc();
      } else if (order == SortOrder.DESC) {
        return desc();
      }
      throw new RuntimeException("something went wrong");
    }

    @Override
    public Builder missing(String value) {
      wrappedBuilder.missing(value);
      return this;
    }

    @Override
    public DataStoreFieldSort build() {
      final var fieldSort = wrappedBuilder.build();
      return new ElasticsearchFieldSort(fieldSort);
    }
  }
}
