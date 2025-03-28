/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import io.camunda.util.ObjectBuilder;
import java.util.Map;

public record SearchAggregationResult(
    Long docCount, Map<String, SearchAggregationResult> aggregations) {

  public static final class Builder implements ObjectBuilder<SearchAggregationResult> {

    private Long docCount;
    private Map<String, SearchAggregationResult> aggregations;

    public Builder docCount(final Long value) {
      docCount = value;
      return this;
    }

    public Builder aggregations(final Map<String, SearchAggregationResult> value) {
      aggregations = value;
      return this;
    }

    @Override
    public SearchAggregationResult build() {
      return new SearchAggregationResult(docCount, aggregations);
    }
  }
}
