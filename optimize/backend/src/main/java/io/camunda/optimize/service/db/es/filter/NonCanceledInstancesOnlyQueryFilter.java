/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.INTERNALLY_TERMINATED_STATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.NonCanceledInstancesOnlyFilterDataDto;
import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class NonCanceledInstancesOnlyQueryFilter
    implements QueryFilter<NonCanceledInstancesOnlyFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<NonCanceledInstancesOnlyFilterDataDto> nonCanceledInstancesOnlyFilters,
      final FilterContext filterContext) {
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
