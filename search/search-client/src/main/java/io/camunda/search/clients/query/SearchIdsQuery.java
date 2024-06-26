/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final record SearchIdsQuery(List<String> values) implements SearchQueryOption {

  static SearchIdsQuery of(final Function<Builder, ObjectBuilder<SearchIdsQuery>> fn) {
    return SearchQueryBuilders.ids(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchIdsQuery> {

    private List<String> ids;

    public Builder values(final List<String> values) {
      ids = addValuesToList(ids, values);
      return this;
    }

    public Builder values(final String value, final String... values) {
      return values(collectValues(value, values));
    }

    @Override
    public SearchIdsQuery build() {
      return new SearchIdsQuery(Objects.requireNonNullElse(ids, Collections.emptyList()));
    }
  }
}
