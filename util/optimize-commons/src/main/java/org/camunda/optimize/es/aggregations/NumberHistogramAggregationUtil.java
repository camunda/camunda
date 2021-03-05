/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.es.aggregations;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NumberHistogramAggregationUtil {

  public static HistogramAggregationBuilder generateHistogramWithField(final String histogramName, final double intervalSize,
                                                                final double offsetValue, final double max,
                                                                final String fieldName, final String formatString,
                                                                final AggregationBuilder subAggregation) {
    return AggregationBuilders
      .histogram(histogramName)
      .interval(intervalSize)
      .offset(offsetValue)
      .field(fieldName)
      .extendedBounds(offsetValue, max)
      .format(formatString)
      .subAggregation(subAggregation);
  }

  public static HistogramAggregationBuilder generateHistogramFromScript(final String histogramName, final double intervalSize,
                                                                 final double offsetValue,
                                                                 final Script script, final double max,
                                                                 final AggregationBuilder subAggregation) {
    return AggregationBuilders
      .histogram(histogramName)
      .interval(intervalSize)
      .offset(offsetValue)
      .script(script)
      .extendedBounds(offsetValue, max)
      .subAggregation(subAggregation);
  }
}
