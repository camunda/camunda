/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.sort;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record SearchSortOptions(SearchFieldSort field) {

  public static SearchSortOptions of(final Function<Builder, ObjectBuilder<SearchSortOptions>> fn) {
    return SortOptionsBuilders.sort(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchSortOptions> {

    private SearchFieldSort field;

    public Builder field(final SearchFieldSort value) {
      field = value;
      return this;
    }

    public Builder field(
        final Function<SearchFieldSort.Builder, ObjectBuilder<SearchFieldSort>> fn) {
      return field(SortOptionsBuilders.field(fn));
    }

    @Override
    public SearchSortOptions build() {
      return new SearchSortOptions(Objects.requireNonNull(field));
    }
  }
}
