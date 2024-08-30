/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.END_DATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CompletedInstancesOnlyFilterDataDto;
import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class CompletedInstancesOnlyQueryFilter
    implements QueryFilter<CompletedInstancesOnlyFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<CompletedInstancesOnlyFilterDataDto> completedInstancesData,
      final FilterContext filterContext) {
    if (completedInstancesData != null && !completedInstancesData.isEmpty()) {
      final List<QueryBuilder> filters = query.filter();

      final BoolQueryBuilder onlyCompletedQuery = boolQuery().must(existsQuery(END_DATE));

      filters.add(onlyCompletedQuery);
    }
  }
}
