/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import java.util.List;
import java.util.Objects;

public record SearchCardinalityAggregator(
    String name, String field, String script, String lang, List<SearchAggregator> aggregations)
    implements SearchAggregator {

  @Override
  public String getName() {
    return name;
  }

  @Override
  public List<SearchAggregator> getAggregations() {
    return aggregations;
  }

  public static final class Builder extends SearchAggregator.AbstractBuilder<Builder> {
    private String field;
    private String script;
    private String lang;

    @Override
    protected Builder self() {
      return this;
    }

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder script(final String value) {
      script = value;
      return this;
    }

    public Builder lang(final String value) {
      lang = value;
      return this;
    }

    public SearchCardinalityAggregator build() {
      return new SearchCardinalityAggregator(
          Objects.requireNonNull(name, "Expected non-null field for name."),
          field,
          script,
          lang,
          aggregations);
    }
  }
}
