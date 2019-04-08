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

import java.util.List;

import static org.camunda.optimize.service.es.filter.CanceledInstancesOnlyQueryFilter.EXTERNALLY_TERMINATED;
import static org.camunda.optimize.service.es.filter.CanceledInstancesOnlyQueryFilter.INTERNALLY_TERMINATED;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class NonCanceledInstancesOnlyQueryFilter implements QueryFilter<NonCanceledInstancesOnlyFilterDataDto> {
  @Override
  public void addFilters(BoolQueryBuilder query, List<NonCanceledInstancesOnlyFilterDataDto>
    nonCanceledInstancesOnlyFilters) {
    if (nonCanceledInstancesOnlyFilters != null && !nonCanceledInstancesOnlyFilters.isEmpty()) {
      List<QueryBuilder> filters = query.filter();

      BoolQueryBuilder onlyNonCanceledInstancesQuery =
        boolQuery()
          .mustNot(termQuery(STATE, EXTERNALLY_TERMINATED))
          .mustNot(termQuery(STATE, INTERNALLY_TERMINATED));

      filters.add(onlyNonCanceledInstancesQuery);
    }
  }
}
