/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.NonSuspendedInstancesOnlyFilterDataDto;
import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class NonSuspendedInstancesOnlyQueryFilter
    implements QueryFilter<NonSuspendedInstancesOnlyFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<NonSuspendedInstancesOnlyFilterDataDto> nonSuspendedInstancesOnlyFilters,
      final FilterContext filterContext) {
    if (nonSuspendedInstancesOnlyFilters != null && !nonSuspendedInstancesOnlyFilters.isEmpty()) {
      List<QueryBuilder> filters = query.filter();

      BoolQueryBuilder onlyNonSuspendedInstancesQuery =
          boolQuery().mustNot(termQuery(STATE, SUSPENDED_STATE));

      filters.add(onlyNonSuspendedInstancesQuery);
    }
  }
}
