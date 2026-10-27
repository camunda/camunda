/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.service.db.os.report.aggregations.AggregationStrategyOS;
import io.camunda.optimize.service.db.os.report.aggregations.AvgAggregationOS;
import io.camunda.optimize.service.db.os.report.aggregations.MaxAggregationOS;
import io.camunda.optimize.service.db.os.report.aggregations.MinAggregationOS;
import io.camunda.optimize.service.db.os.report.aggregations.PercentileAggregationOS;
import io.camunda.optimize.service.db.os.report.aggregations.SumAggregationOS;
import io.camunda.optimize.service.db.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreter;

public abstract class AbstractProcessViewMultiAggregationInterpreterOS
    extends AbstractProcessViewMultiAggregationInterpreter<AggregationStrategyOS>
    implements ProcessViewInterpreterOS {

  @Override
  protected AggregationStrategyOS getAggregationStrategy(final AggregationDto aggregationDto) {
    return switch (aggregationDto.getType()) {
      case MIN -> new MinAggregationOS();
      case MAX -> new MaxAggregationOS();
      case AVERAGE -> new AvgAggregationOS();
      case SUM -> new SumAggregationOS();
      case PERCENTILE -> new PercentileAggregationOS(aggregationDto.getValue());
    };
  }
}
