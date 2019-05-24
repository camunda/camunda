/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.script.Script;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScript;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

public class FlowNodeExecutionStateAggregationUtil {

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
        throw new OptimizeRuntimeException(String.format(
          "Unknown flow node execution state [%s]",
          flowNodeExecutionState
        ));
    }
  }

  public static Script getAggregationScript(long currRequestDateInMs,
                                            String durationFieldName,
                                            String referenceDateFieldName) {
    final Map<String, Object> params = new HashMap<>();
    params.put("currRequestDateInMs", currRequestDateInMs);
    params.put("durFieldName", durationFieldName);
    params.put("refDateFieldName", referenceDateFieldName);

    // @formatter:off
    return createDefaultScript(
      "if (doc[params.durFieldName].empty && !doc[params.refDateFieldName].empty) {" +
          "return params.currRequestDateInMs - doc[params.refDateFieldName].date.getMillis() " +
        "} else { " +
          "return doc[params.durFieldName] " +
        "}",
      params
    );
    // @formatter:on
  }
}
