/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.db.rdbms.sql.SearchColumn;
import io.camunda.search.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record DbQuerySorting<T>(List<SortingEntry<T>> orderings) {

  public static <T> DbQuerySorting<T> of(
      final Function<DbQuerySorting.Builder<T>, ObjectBuilder<DbQuerySorting<T>>> fn) {
    return fn.apply(new DbQuerySorting.Builder<>()).build();
  }

  public static final class Builder<T> implements ObjectBuilder<DbQuerySorting<T>> {

    private final List<SortingEntry<T>> entries;

    public Builder() {
      entries = new ArrayList<>();
    }

    public Builder<T> addEntry(final SearchColumn<T> column, final SortOrder order) {
      entries.add(new SortingEntry<>(column, order));
      return this;
    }

    @Override
    public DbQuerySorting<T> build() {
      return new DbQuerySorting<>(entries);
    }
  }

  public record SortingEntry<T>(SearchColumn<T> column, SortOrder order) {}
}
