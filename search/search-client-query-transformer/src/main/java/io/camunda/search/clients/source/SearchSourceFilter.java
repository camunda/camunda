/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.source;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record SearchSourceFilter(List<String> includes, List<String> excludes) {
  public static SearchSourceFilter of(
      final Function<SearchSourceFilter.Builder, ObjectBuilder<SearchSourceFilter>> fn) {
    return SourceConfigBuilders.filter(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchSourceFilter> {

    private List<String> includes;
    private List<String> excludes;

    public SearchSourceFilter.Builder includes(final List<String> value) {
      includes = value;
      return this;
    }

    public SearchSourceFilter.Builder excludes(final List<String> value) {
      excludes = value;
      return this;
    }

    @Override
    public SearchSourceFilter build() {
      return new SearchSourceFilter(includes, excludes);
    }
  }
}
