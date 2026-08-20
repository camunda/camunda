/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.util;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;

public abstract class AbstractProcessPartQueryUtil {
  protected static final String SCRIPT_AGGREGATION = "scriptAggregation";
  protected static final String NESTED_AGGREGATION = "nestedAggregation";

  protected static String getScriptAggregationName(final AggregationDto aggregationDto) {
    final String aggName =
        aggregationDto.getType() == AggregationType.PERCENTILE
            ? aggregationDto.getType().getId()
                + String.valueOf(aggregationDto.getValue()).replace(".", "_")
            : aggregationDto.getType().getId();
    return String.join("_", SCRIPT_AGGREGATION, aggName);
  }
}
