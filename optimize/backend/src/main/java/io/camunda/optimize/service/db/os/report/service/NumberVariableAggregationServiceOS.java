/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.service;

import static io.camunda.optimize.service.db.os.report.interpreter.util.NumberHistogramAggregationUtilOS.generateHistogramWithField;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.VARIABLE_HISTOGRAM_AGGREGATION;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.os.report.context.VariableAggregationContextOS;
import io.camunda.optimize.service.db.report.interpreter.service.AbstractNumberVariableAggregationService;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.springframework.stereotype.Component;

@Component
public class NumberVariableAggregationServiceOS extends AbstractNumberVariableAggregationService {

  public NumberVariableAggregationServiceOS() {}

  public Optional<Pair<String, Aggregation>> createNumberVariableAggregation(
      final VariableAggregationContextOS context) {
    if (context.getVariableRangeMinMaxStats().isEmpty()) {
      return Optional.empty();
    }

    final Optional<Double> min = getBaselineForNumberVariableAggregation(context);
    if (min.isEmpty()) {
      // no valid baseline is set, return empty result
      return Optional.empty();
    }

    final double intervalSize = getIntervalSize(context, min.get());
    final double max = context.getMaxVariableValue();

    final String digitFormat = VariableType.DOUBLE.equals(context.getVariableType()) ? "0.00" : "0";

    return Optional.of(
        generateHistogramWithField(
            VARIABLE_HISTOGRAM_AGGREGATION,
            intervalSize,
            min.get(),
            max,
            context.getNestedVariableValueFieldLabel(),
            digitFormat,
            context.getSubAggregations()));
  }
}
