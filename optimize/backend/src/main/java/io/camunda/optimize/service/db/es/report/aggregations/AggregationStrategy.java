/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.aggregations;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

public abstract class AggregationStrategy<T extends ValuesSourceAggregationBuilder<T>> {

  protected abstract ValuesSourceAggregationBuilder<T> createAggregationBuilderForAggregation(
      final String customIdentifier);

  protected abstract Double getValueForAggregation(
      final String customIdentifier, final Aggregations aggs);

  public abstract AggregationDto getAggregationType();

  public Double getValue(final Aggregations aggs) {
    return getValue(null, aggs);
  }

  public Double getValue(final String customIdentifier, final Aggregations aggs) {
    return getValueForAggregation(customIdentifier, aggs);
  }

  public ValuesSourceAggregationBuilder<T> createAggregationBuilder() {
    return createAggregationBuilder(null);
  }

  public ValuesSourceAggregationBuilder<T> createAggregationBuilder(final String customIdentifier) {
    return createAggregationBuilderForAggregation(customIdentifier);
  }

  protected String createAggregationName(final String... segments) {
    return Arrays.stream(segments).filter(Objects::nonNull).collect(Collectors.joining("_"));
  }
}
