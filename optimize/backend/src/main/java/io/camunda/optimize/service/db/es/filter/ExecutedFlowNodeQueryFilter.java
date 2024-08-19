/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import java.util.List;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * The executed flow node catches any flow nodes that are completed or still running, including
 * those that are marked as canceled
 */
@Component
public class ExecutedFlowNodeQueryFilter implements QueryFilter<ExecutedFlowNodeFilterDataDto> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ExecutedFlowNodeQueryFilter.class);

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<ExecutedFlowNodeFilterDataDto> flowNodeFilter,
      final FilterContext filterContext) {
    final List<QueryBuilder> filters = query.filter();
    for (final ExecutedFlowNodeFilterDataDto executedFlowNode : flowNodeFilter) {
      filters.add(createFilterQueryBuilder(executedFlowNode));
    }
  }

  private QueryBuilder createFilterQueryBuilder(
      final ExecutedFlowNodeFilterDataDto flowNodeFilter) {
    final BoolQueryBuilder boolQueryBuilder = boolQuery();
    if (MembershipFilterOperator.IN == flowNodeFilter.getOperator()) {
      for (final String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.should(
            nestedQuery(
                FLOW_NODE_INSTANCES,
                termQuery(nestedFlowNodeIdFieldLabel(), value),
                ScoreMode.None));
      }
    } else if (MembershipFilterOperator.NOT_IN == flowNodeFilter.getOperator()) {
      for (final String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.mustNot(
            nestedQuery(
                FLOW_NODE_INSTANCES,
                termQuery(nestedFlowNodeIdFieldLabel(), value),
                ScoreMode.None));
      }
    } else {
      log.error(
          "Could not filter for flow nodes. "
              + "Operator [{}] is not allowed! Use either [in] or [not in]",
          flowNodeFilter.getOperator());
    }
    return boolQueryBuilder;
  }

  private String nestedFlowNodeIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }
}
