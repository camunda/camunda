/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.SuspendedInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class SuspendedInstancesOnlyQueryFilter implements QueryFilter<SuspendedInstancesOnlyFilterDataDto> {
  public static String SUSPENDED = "SUSPENDED";

  @Override
  public void addFilters(BoolQueryBuilder query,
                         List<SuspendedInstancesOnlyFilterDataDto> suspendedInstancesOnlyFilters) {
    if (suspendedInstancesOnlyFilters != null && !suspendedInstancesOnlyFilters.isEmpty()) {
      List<QueryBuilder> filters = query.filter();

      BoolQueryBuilder onlySuspendedInstances = boolQuery().should(termQuery(STATE, SUSPENDED));

      filters.add(onlySuspendedInstances);
    }
  }
}
