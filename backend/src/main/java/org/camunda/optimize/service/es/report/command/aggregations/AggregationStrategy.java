/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class AggregationStrategy<T extends ValuesSourceAggregationBuilder<T>> {

  protected abstract ValuesSourceAggregationBuilder<T> createAggregationBuilderForAggregation(final String customIdentifier);

  protected abstract Double getValueForAggregation(final String customIdentifier, final Aggregations aggs);

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
