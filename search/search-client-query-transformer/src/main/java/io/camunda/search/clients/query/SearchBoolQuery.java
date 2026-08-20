/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import static io.camunda.util.CollectionUtil.addValuesToList;

import io.camunda.util.ObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record SearchBoolQuery(
    List<SearchQuery> filter,
    List<SearchQuery> must,
    List<SearchQuery> mustNot,
    List<SearchQuery> should)
    implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchBoolQuery> {

    private List<SearchQuery> filter;
    private List<SearchQuery> must;
    private List<SearchQuery> mustNot;
    private List<SearchQuery> should;

    public Builder filter(final List<SearchQuery> queries) {
      filter = addValuesToList(filter, queries);
      return this;
    }

    public Builder must(final List<SearchQuery> queries) {
      must = addValuesToList(must, queries);
      return this;
    }

    public Builder mustNot(final List<SearchQuery> queries) {
      mustNot = addValuesToList(mustNot, queries);
      return this;
    }

    public Builder should(final List<SearchQuery> queries) {
      should = addValuesToList(should, queries);
      return this;
    }

    @Override
    public SearchBoolQuery build() {
      return new SearchBoolQuery(
          Objects.requireNonNullElse(filter, Collections.emptyList()),
          Objects.requireNonNullElse(must, Collections.emptyList()),
          Objects.requireNonNullElse(mustNot, Collections.emptyList()),
          Objects.requireNonNullElse(should, Collections.emptyList()));
    }
  }
}
