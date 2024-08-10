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
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutingFlowNodeFilterDataDto;
import java.util.List;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class ExecutingFlowNodeQueryFilter implements QueryFilter<ExecutingFlowNodeFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<ExecutingFlowNodeFilterDataDto> flowNodeFilter,
      final FilterContext filterContext) {
    List<QueryBuilder> filters = query.filter();
    flowNodeFilter.forEach(filter -> filters.add(createFilterQueryBuilder(filter)));
  }

  private QueryBuilder createFilterQueryBuilder(
      final ExecutingFlowNodeFilterDataDto flowNodeFilter) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    flowNodeFilter
        .getValues()
        .forEach(
            flowNodeId ->
                boolQueryBuilder.should(
                    nestedQuery(
                        FLOW_NODE_INSTANCES,
                        boolQuery()
                            .must(termQuery(nestedActivityIdFieldLabel(), flowNodeId))
                            .mustNot(existsQuery(nestedEndDateFieldLabel())),
                        ScoreMode.None)));
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID;
  }

  private String nestedEndDateFieldLabel() {
    return FLOW_NODE_INSTANCES + "." + END_DATE;
  }
}
