/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.NonSuspendedInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class NonSuspendedInstancesOnlyQueryFilter implements QueryFilter<NonSuspendedInstancesOnlyFilterDataDto> {
  @Override
  public void addFilters(final BoolQueryBuilder query,
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
