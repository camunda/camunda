/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutingFlowNodeFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ExecutingFlowNodeQueryFilterES
    implements QueryFilterES<ExecutingFlowNodeFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<ExecutingFlowNodeFilterDataDto> flowNodeFilter,
      final FilterContext filterContext) {
    if (!flowNodeFilter.isEmpty()) {
      flowNodeFilter.forEach(
          filter -> query.filter(f -> f.bool(createFilterQueryBuilder(filter).build())));
    }
  }

  private BoolQuery.Builder createFilterQueryBuilder(
      final ExecutingFlowNodeFilterDataDto flowNodeFilter) {
    final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
    flowNodeFilter
        .getValues()
        .forEach(
            flowNodeId ->
                boolQueryBuilder.should(
                    s ->
                        s.nested(
                            n ->
                                n.path(FLOW_NODE_INSTANCES)
                                    .query(
                                        q ->
                                            q.bool(
                                                b ->
                                                    b.must(
                                                            m ->
                                                                m.term(
                                                                    t ->
                                                                        t.field(
                                                                                nestedActivityIdFieldLabel())
                                                                            .value(flowNodeId)))
                                                        .mustNot(
                                                            m ->
                                                                m.exists(
                                                                    e ->
                                                                        e.field(
                                                                            nestedEndDateFieldLabel())))))
                                    .scoreMode(ChildScoreMode.None))));
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  private String nestedEndDateFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + END_DATE;
  }
}
