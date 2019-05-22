/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

public class ExecutionStateAggregationUtil {

  public static BoolQueryBuilder addExecutionStateFilter(BoolQueryBuilder boolQueryBuilder,
                                                         FlowNodeExecutionState flowNodeExecutionState,
                                                         String qualifyingFilterField) {
    switch (flowNodeExecutionState) {
      case RUNNING:
        return boolQueryBuilder.mustNot(existsQuery(qualifyingFilterField));
      case COMPLETED:
        return boolQueryBuilder.must(existsQuery(qualifyingFilterField));
      case ALL:
        return boolQueryBuilder;
      default:
        throw new OptimizeRuntimeException(String.format("Unknown flow node execution state [%s]", flowNodeExecutionState));
    }
  }
}
