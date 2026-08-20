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

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.MembershipFilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutedFlowNodeFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * The executed flow node catches any flow nodes that are completed or still running, including
 * those that are marked as canceled
 */
@Component
@Conditional(ElasticSearchCondition.class)
public class ExecutedFlowNodeQueryFilterES implements QueryFilterES<ExecutedFlowNodeFilterDataDto> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ExecutedFlowNodeQueryFilterES.class);

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<ExecutedFlowNodeFilterDataDto> flowNodeFilter,
      final FilterContext filterContext) {
    query.filter(flowNodeFilter.stream().map(this::createFilterQueryBuilder).toList());
  }

  private Query createFilterQueryBuilder(final ExecutedFlowNodeFilterDataDto flowNodeFilter) {
    final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
    if (MembershipFilterOperator.IN == flowNodeFilter.getOperator()) {
      for (final String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.should(
            s ->
                s.nested(
                    n ->
                        n.path(FLOW_NODE_INSTANCES)
                            .query(
                                q ->
                                    q.term(t -> t.field(nestedFlowNodeIdFieldLabel()).value(value)))
                            .scoreMode(ChildScoreMode.None)));
      }
    } else if (MembershipFilterOperator.NOT_IN == flowNodeFilter.getOperator()) {
      for (final String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.mustNot(
            s ->
                s.nested(
                    n ->
                        n.path(FLOW_NODE_INSTANCES)
                            .query(
                                q ->
                                    q.term(t -> t.field(nestedFlowNodeIdFieldLabel()).value(value)))
                            .scoreMode(ChildScoreMode.None)));
      }
    } else {
      LOG.error(
          "Could not filter for flow nodes. "
              + "Operator [{}] is not allowed! Use either [in] or [not in]",
          flowNodeFilter.getOperator());
    }
    return Query.of(b -> b.bool(boolQueryBuilder.build()));
  }

  private String nestedFlowNodeIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }
}
