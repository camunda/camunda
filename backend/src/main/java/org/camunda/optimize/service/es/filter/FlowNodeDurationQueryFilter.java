/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.FlowNodeDurationFiltersDataDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.AggregationFilterUtil.getDurationFilterScript;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_DURATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class FlowNodeDurationQueryFilter implements QueryFilter<FlowNodeDurationFiltersDataDto> {

  public void addFilters(final BoolQueryBuilder query,
                         final List<FlowNodeDurationFiltersDataDto> durationFilters,
                         final ZoneId timezone) {
    if (!CollectionUtils.isEmpty(durationFilters)) {
      final List<QueryBuilder> filters = query.filter();
      for (FlowNodeDurationFiltersDataDto flowNodeDurationFilterDto : durationFilters) {
        final BoolQueryBuilder disjunctMultiFlowNodeQuery = boolQuery().minimumShouldMatch(1);
        flowNodeDurationFilterDto.forEach((flowNodeId, durationFilter) -> {
          final BoolQueryBuilder particularFlowNodeQuery = boolQuery()
            .must(termQuery(nestedFieldReference(ACTIVITY_ID), flowNodeId))
            .must(QueryBuilders.scriptQuery(getDurationFilterScript(
              LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
              nestedFieldReference(ACTIVITY_DURATION),
              nestedFieldReference(ACTIVITY_START_DATE),
              durationFilter
            )));
          disjunctMultiFlowNodeQuery.should(particularFlowNodeQuery);
        });
        filters.add(nestedQuery(EVENTS, disjunctMultiFlowNodeQuery, ScoreMode.None));
      }
    }
  }

  private String nestedFieldReference(final String nestedField) {
    return EVENTS + "." + nestedField;
  }

}
