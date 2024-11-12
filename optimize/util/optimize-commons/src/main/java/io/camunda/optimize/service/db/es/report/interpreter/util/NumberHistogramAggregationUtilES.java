/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.util;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import java.util.Map;

public final class NumberHistogramAggregationUtilES {

  private NumberHistogramAggregationUtilES() {}

  public static Aggregation.Builder.ContainerBuilder generateHistogramWithField(
      final String histogramName,
      final double intervalSize,
      final double offsetValue,
      final double max,
      final String fieldName,
      final String formatString,
      final Map<String, Aggregation.Builder.ContainerBuilder> subAggregations) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .histogram(
                h ->
                    h.interval(intervalSize)
                        .offset(offsetValue)
                        .field(fieldName)
                        .format(formatString)
                        .extendedBounds(e -> e.min(offsetValue).max(max)));
    subAggregations.forEach((k, v) -> builder.aggregations(k, v.build()));
    return builder;
  }

  public static Aggregation.Builder.ContainerBuilder generateHistogramFromScript(
      final String histogramName,
      final double intervalSize,
      final double offsetValue,
      final Script script,
      final double max,
      final Map<String, Aggregation.Builder.ContainerBuilder> subAggregations) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .histogram(
                h ->
                    h.interval(intervalSize)
                        .offset(offsetValue)
                        .script(script)
                        .extendedBounds(e -> e.min(offsetValue).max(max)));
    subAggregations.forEach((k, v) -> builder.aggregations(k, v.build()));
    return builder;
  }
}
