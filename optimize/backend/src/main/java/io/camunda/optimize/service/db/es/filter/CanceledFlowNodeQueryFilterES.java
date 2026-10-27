/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_CANCELED;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledFlowNodeFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class CanceledFlowNodeQueryFilterES implements QueryFilterES<CanceledFlowNodeFilterDataDto> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CanceledFlowNodeQueryFilterES.class);

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<CanceledFlowNodeFilterDataDto> flowNodeFilter,
      final FilterContext filterContext) {
    query.filter(flowNodeFilter.stream().map(this::createFilterQueryBuilder).toList());
  }

  private Query createFilterQueryBuilder(final CanceledFlowNodeFilterDataDto flowNodeFilter) {
    final Query.Builder builder = new Query.Builder();
    builder.bool(
        b -> {
          for (final String value : flowNodeFilter.getValues()) {
            b.should(
                s ->
                    s.nested(
                        n ->
                            n.path(FLOW_NODE_INSTANCES)
                                .query(
                                    q ->
                                        q.bool(
                                            bb ->
                                                bb.must(
                                                        m ->
                                                            m.bool(
                                                                new BoolQuery.Builder()
                                                                    .must(
                                                                        r ->
                                                                            r.exists(
                                                                                e ->
                                                                                    e.field(
                                                                                        nestedCanceledFieldLabel())))
                                                                    .must(
                                                                        r ->
                                                                            r.term(
                                                                                t ->
                                                                                    t.field(
                                                                                            nestedCanceledFieldLabel())
                                                                                        .value(
                                                                                            true)))
                                                                    .build()))
                                                    .must(
                                                        m ->
                                                            m.term(
                                                                t ->
                                                                    t.field(
                                                                            nestedActivityIdFieldLabel())
                                                                        .value(value)))))
                                .scoreMode(ChildScoreMode.None)));
          }
          return b;
        });
    return builder.build();
  }

  private String nestedActivityIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  private String nestedCanceledFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_CANCELED;
  }
}
