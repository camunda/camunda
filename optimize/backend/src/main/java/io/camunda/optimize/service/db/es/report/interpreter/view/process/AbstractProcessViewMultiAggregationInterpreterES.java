/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.service.db.es.report.aggregations.AggregationStrategyES;
import io.camunda.optimize.service.db.es.report.aggregations.AvgAggregationES;
import io.camunda.optimize.service.db.es.report.aggregations.MaxAggregationES;
import io.camunda.optimize.service.db.es.report.aggregations.MinAggregationES;
import io.camunda.optimize.service.db.es.report.aggregations.PercentileAggregationES;
import io.camunda.optimize.service.db.es.report.aggregations.SumAggregationES;
import io.camunda.optimize.service.db.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreter;

public abstract class AbstractProcessViewMultiAggregationInterpreterES
    extends AbstractProcessViewMultiAggregationInterpreter<AggregationStrategyES<?>>
    implements ProcessViewInterpreterES {

  @Override
  protected AggregationStrategyES<?> getAggregationStrategy(final AggregationDto aggregationDto) {
    return switch (aggregationDto.getType()) {
      case MIN -> new MinAggregationES();
      case MAX -> new MaxAggregationES();
      case AVERAGE -> new AvgAggregationES();
      case SUM -> new SumAggregationES();
      case PERCENTILE -> new PercentileAggregationES(aggregationDto.getValue());
    };
  }
}
