/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.util;

import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public final class NumberHistogramAggregationUtilOS {

  private NumberHistogramAggregationUtilOS() {}

  public static Pair<String, Aggregation> generateHistogramWithField(
      final String histogramName,
      final double intervalSize,
      final double offsetValue,
      final double max,
      final String fieldName,
      final String formatString,
      final Map<String, Aggregation> subAggregations) {
    return Pair.of(
        histogramName,
        new Aggregation.Builder()
            .histogram(
                b ->
                    b.interval(intervalSize)
                        .offset(offsetValue)
                        .field(fieldName)
                        .extendedBounds(b1 -> b1.min(offsetValue).max(max))
                        .format(formatString))
            .aggregations(subAggregations)
            .build());
  }

  public static Pair<String, Aggregation> generateHistogramFromScript(
      final String histogramName,
      final double intervalSize,
      final double offsetValue,
      final Script script,
      final double max,
      final Map<String, Aggregation> subAggregations) {
    return Pair.of(
        histogramName,
        new Aggregation.Builder()
            .histogram(
                b ->
                    b.interval(intervalSize)
                        .offset(offsetValue)
                        .script(script)
                        .extendedBounds(b1 -> b1.min(offsetValue).max(max)))
            .aggregations(subAggregations)
            .build());
  }
}
