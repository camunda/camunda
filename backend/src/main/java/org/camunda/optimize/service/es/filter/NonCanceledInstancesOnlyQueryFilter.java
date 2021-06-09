/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.NonCanceledInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.INTERNALLY_TERMINATED_STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class NonCanceledInstancesOnlyQueryFilter implements QueryFilter<NonCanceledInstancesOnlyFilterDataDto> {
  @Override
  public void addFilters(BoolQueryBuilder query, List<NonCanceledInstancesOnlyFilterDataDto>
    nonCanceledInstancesOnlyFilters, final ZoneId timezone, final boolean isUserTaskReport) {
    if (nonCanceledInstancesOnlyFilters != null && !nonCanceledInstancesOnlyFilters.isEmpty()) {
      List<QueryBuilder> filters = query.filter();

      BoolQueryBuilder onlyNonCanceledInstancesQuery =
        boolQuery()
          .mustNot(termQuery(STATE, EXTERNALLY_TERMINATED_STATE))
          .mustNot(termQuery(STATE, INTERNALLY_TERMINATED_STATE));

      filters.add(onlyNonCanceledInstancesQuery);
    }
  }
}
