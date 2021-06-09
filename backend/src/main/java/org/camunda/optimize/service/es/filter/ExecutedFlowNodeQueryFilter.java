/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.IN;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_IN;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * The executed flow node catches any flow nodes that are completed or still running, including those that are marked
 * as canceled
 */
@Slf4j
@Component
public class ExecutedFlowNodeQueryFilter implements QueryFilter<ExecutedFlowNodeFilterDataDto> {

  @Override
  public void addFilters(BoolQueryBuilder query, List<ExecutedFlowNodeFilterDataDto> flowNodeFilter,
                         final ZoneId timezone, final boolean isUserTaskReport) {
    List<QueryBuilder> filters = query.filter();
    for (ExecutedFlowNodeFilterDataDto executedFlowNode : flowNodeFilter) {
      filters.add(createFilterQueryBuilder(executedFlowNode));
    }
  }

  private QueryBuilder createFilterQueryBuilder(ExecutedFlowNodeFilterDataDto flowNodeFilter) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    if (IN.equals(flowNodeFilter.getOperator())) {
      for (String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.should(
          nestedQuery(
            FLOW_NODE_INSTANCES,
            termQuery(nestedActivityIdFieldLabel(), value),
            ScoreMode.None
          )
        );
      }
    } else if (NOT_IN.equals(flowNodeFilter.getOperator())) {
      for (String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.mustNot(
          nestedQuery(
            FLOW_NODE_INSTANCES,
            termQuery(nestedActivityIdFieldLabel(), value),
            ScoreMode.None
          )
        );
      }
    } else {
      log.error("Could not filter for flow nodes. " +
                  "Operator [{}] is not allowed! Use either [in] or [not in]", flowNodeFilter.getOperator());
    }
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }
}
