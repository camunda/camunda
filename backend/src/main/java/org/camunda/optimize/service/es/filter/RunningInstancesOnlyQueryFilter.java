/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.RunningInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

@Component
public class RunningInstancesOnlyQueryFilter implements QueryFilter<RunningInstancesOnlyFilterDataDto> {

  public void addFilters(final BoolQueryBuilder query,
                         final List<RunningInstancesOnlyFilterDataDto> runningInstancesOnlyData,
                         final FilterContext filterContext) {
    if (runningInstancesOnlyData != null && !runningInstancesOnlyData.isEmpty()) {
      final List<QueryBuilder> filters = query.filter();

      final BoolQueryBuilder onlyRunningInstancesQuery = boolQuery().mustNot(existsQuery(END_DATE));

      filters.add(onlyRunningInstancesQuery);
    }
  }

}
