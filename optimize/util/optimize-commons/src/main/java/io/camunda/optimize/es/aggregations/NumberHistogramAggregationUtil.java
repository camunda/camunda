/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.es.aggregations;

import java.util.List;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;

public class NumberHistogramAggregationUtil {

  private NumberHistogramAggregationUtil() {}

  public static HistogramAggregationBuilder generateHistogramWithField(
      final String histogramName,
      final double intervalSize,
      final double offsetValue,
      final double max,
      final String fieldName,
      final String formatString,
      final List<AggregationBuilder> subAggregations) {
    final HistogramAggregationBuilder histogramAggregationBuilder =
        AggregationBuilders.histogram(histogramName)
            .interval(intervalSize)
            .offset(offsetValue)
            .field(fieldName)
            .extendedBounds(offsetValue, max)
            .format(formatString);
    subAggregations.forEach(histogramAggregationBuilder::subAggregation);
    return histogramAggregationBuilder;
  }

  public static HistogramAggregationBuilder generateHistogramFromScript(
      final String histogramName,
      final double intervalSize,
      final double offsetValue,
      final Script script,
      final double max,
      final List<AggregationBuilder> subAggregations) {
    final HistogramAggregationBuilder histogramAggregationBuilder =
        AggregationBuilders.histogram(histogramName)
            .interval(intervalSize)
            .offset(offsetValue)
            .script(script)
            .extendedBounds(offsetValue, max);
    subAggregations.forEach(histogramAggregationBuilder::subAggregation);
    return histogramAggregationBuilder;
  }
}
