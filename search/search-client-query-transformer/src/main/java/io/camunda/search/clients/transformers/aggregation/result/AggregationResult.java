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

public record AggregationResult(Long docCount, Map<String, AggregationResult> aggregations) {

  public static final class Builder implements ObjectBuilder<AggregationResult> {

    private Long docCount;
    private Map<String, AggregationResult> aggregations;

    public Builder docCount(final Long value) {
      docCount = value;
      return this;
    }

    public Builder aggregations(final Map<String, AggregationResult> value) {
      aggregations = value;
      return this;
    }

    @Override
    public AggregationResult build() {
      return new AggregationResult(docCount, aggregations);
    }
  }
}
