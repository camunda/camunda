/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createFlowNodeEndDateFilterQuery;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.flownode.FlowNodeDateFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class FlowNodeEndDateQueryFilterES implements QueryFilterES<FlowNodeDateFilterDataDto<?>> {

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<FlowNodeDateFilterDataDto<?>> flowNodeEndDateFilters,
      final FilterContext filterContext) {
    if (!flowNodeEndDateFilters.isEmpty()) {
      for (final FlowNodeDateFilterDataDto<?> flowNodeEndDateFilter : flowNodeEndDateFilters) {
        query.filter(
            f ->
                f.nested(
                    n ->
                        n.path(FLOW_NODE_INSTANCES)
                            .query(
                                q ->
                                    q.bool(
                                        createFlowNodeEndDateFilterQuery(
                                                flowNodeEndDateFilter, filterContext.getTimezone())
                                            .build()))
                            .scoreMode(ChildScoreMode.None)));
      }
    }
  }
}
