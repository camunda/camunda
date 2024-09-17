/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.source;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record SearchSourceConfig(SearchSourceFilter sourceFilter) {

  public static SearchSourceConfig of(
      final Function<Builder, ObjectBuilder<SearchSourceConfig>> fn) {
    return SourceConfigBuilders.sourceConfig(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchSourceConfig> {

    private SearchSourceFilter filter;

    public Builder filter(final SearchSourceFilter value) {
      filter = value;
      return this;
    }

    public Builder filter(
        final Function<SearchSourceFilter.Builder, ObjectBuilder<SearchSourceFilter>> fn) {
      return filter(SourceConfigBuilders.filter(fn));
    }

    @Override
    public SearchSourceConfig build() {
      return new SearchSourceConfig(filter);
    }
  }
}
