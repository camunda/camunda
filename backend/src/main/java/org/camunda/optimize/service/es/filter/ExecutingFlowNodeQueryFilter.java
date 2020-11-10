/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.ExecutingFlowNodeFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ExecutingFlowNodeQueryFilter implements QueryFilter<ExecutingFlowNodeFilterDataDto> {

  @Override
  public void addFilters(final BoolQueryBuilder query,
                         final List<ExecutingFlowNodeFilterDataDto> flowNodeFilter,
                         final ZoneId timezone) {
    List<QueryBuilder> filters = query.filter();
    flowNodeFilter.forEach(filter -> filters.add(createFilterQueryBuilder(filter)));
  }

  private QueryBuilder createFilterQueryBuilder(final ExecutingFlowNodeFilterDataDto flowNodeFilter) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    flowNodeFilter.getValues()
      .forEach(flowNodeId -> boolQueryBuilder
        .should(
          nestedQuery(
            EVENTS,
            boolQuery()
              .must(termQuery(nestedActivityIdFieldLabel(), flowNodeId))
              .mustNot(existsQuery(nestedEndDateFieldLabel())),
            ScoreMode.None
          )));
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return EVENTS + "." + ACTIVITY_ID;
  }

  private String nestedEndDateFieldLabel() {
    return EVENTS + "." + END_DATE;
  }

}
