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

public final class SourceConfigBuilders {

  private SourceConfigBuilders() {}

  public static SearchSourceConfig.Builder sourceConfig() {
    return new SearchSourceConfig.Builder();
  }

  public static SearchSourceConfig sourceConfig(
      final Function<SearchSourceConfig.Builder, ObjectBuilder<SearchSourceConfig>> fn) {
    return fn.apply(sourceConfig()).build();
  }

  public static SearchSourceFilter.Builder filter() {
    return new SearchSourceFilter.Builder();
  }

  public static SearchSourceFilter filter(
      final Function<SearchSourceFilter.Builder, ObjectBuilder<SearchSourceFilter>> fn) {
    return fn.apply(filter()).build();
  }
}
