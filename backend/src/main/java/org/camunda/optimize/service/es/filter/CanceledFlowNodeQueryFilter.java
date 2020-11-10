/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledFlowNodeFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Slf4j
@Component
public class CanceledFlowNodeQueryFilter implements QueryFilter<CanceledFlowNodeFilterDataDto> {

  @Override
  public void addFilters(BoolQueryBuilder query, List<CanceledFlowNodeFilterDataDto> flowNodeFilter,
                         final ZoneId timezone) {
    List<QueryBuilder> filters = query.filter();
    for (CanceledFlowNodeFilterDataDto executedFlowNode : flowNodeFilter) {
      filters.add(createFilterQueryBuilder(executedFlowNode));
    }
  }

  private QueryBuilder createFilterQueryBuilder(CanceledFlowNodeFilterDataDto flowNodeFilter) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    final BoolQueryBuilder isCanceledQuery = boolQuery()
      .must(existsQuery(nestedCanceledFieldLabel()))
      .must(termQuery(nestedCanceledFieldLabel(), true));
    for (String value : flowNodeFilter.getValues()) {
      boolQueryBuilder.should(
        nestedQuery(
          EVENTS,
          boolQuery()
            .must(isCanceledQuery)
            .must(termQuery(nestedActivityIdFieldLabel(), value)),
          ScoreMode.None
        ));
    }
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return EVENTS + "." + ACTIVITY_ID;
  }

  private String nestedCanceledFieldLabel() {
    return EVENTS + "." + ACTIVITY_CANCELED;
  }

}
