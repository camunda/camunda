/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CompletedInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

@Component
public class CompletedInstancesOnlyQueryFilter implements QueryFilter<CompletedInstancesOnlyFilterDataDto> {

  public void addFilters(final BoolQueryBuilder query,
                         final List<CompletedInstancesOnlyFilterDataDto> completedInstancesData,
                         final FilterContext filterContext) {
    if (completedInstancesData != null && !completedInstancesData.isEmpty()) {
      final List<QueryBuilder> filters = query.filter();

      final BoolQueryBuilder onlyCompletedQuery = boolQuery().must(existsQuery(END_DATE));

      filters.add(onlyCompletedQuery);
    }
  }

}
