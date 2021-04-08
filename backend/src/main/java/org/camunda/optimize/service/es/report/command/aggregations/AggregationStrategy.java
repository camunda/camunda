/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public interface AggregationStrategy {
  default Double getValue(Aggregations aggs) {
    return getValue(null, aggs);
  }

  Double getValue(String customIdentifier, Aggregations aggs);

  default ValuesSourceAggregationBuilder<?> createAggregationBuilder() {
    return createAggregationBuilder(null);
  }

  ValuesSourceAggregationBuilder<?> createAggregationBuilder(String customIdentifier);

  AggregationType getAggregationType();

  default String createAggregationName(final String... segments) {
    return Arrays.stream(segments).filter(Objects::nonNull).collect(Collectors.joining("_"));
  }
}
