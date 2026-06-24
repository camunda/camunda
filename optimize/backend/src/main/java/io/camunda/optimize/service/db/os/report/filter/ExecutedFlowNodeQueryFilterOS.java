/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * The executed flow node catches any flow nodes that are completed or still running, including
 * those that are marked as canceled
 */
@Component
@Conditional(OpenSearchCondition.class)
public class ExecutedFlowNodeQueryFilterOS implements QueryFilterOS<ExecutedFlowNodeFilterDataDto> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ExecutedFlowNodeQueryFilterOS.class);

  @Override
  public List<Query> filterQueries(
      final List<ExecutedFlowNodeFilterDataDto> flowNodeFilters,
      final FilterContext filterContext) {
    return flowNodeFilters.stream().map(this::createFilterQuery).toList();
  }

  private Query createFilterQuery(final ExecutedFlowNodeFilterDataDto flowNodeFilter) {
    final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
    if (MembershipFilterOperator.IN == flowNodeFilter.getOperator()) {
      for (final String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.should(
            nested(
                FLOW_NODE_INSTANCES,
                term(nestedFlowNodeIdFieldLabel(), value),
                ChildScoreMode.None));
      }
    } else if (MembershipFilterOperator.NOT_IN == flowNodeFilter.getOperator()) {
      for (final String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.mustNot(
            nested(
                FLOW_NODE_INSTANCES,
                term(nestedFlowNodeIdFieldLabel(), value),
                ChildScoreMode.None));
      }
    } else {
      LOG.error(
          "Could not filter for flow nodes. "
              + "Operator [{}] is not allowed! Use either [in] or [not in]",
          flowNodeFilter.getOperator());
    }
    return boolQueryBuilder.build().toQuery();
  }

  private String nestedFlowNodeIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }
}
