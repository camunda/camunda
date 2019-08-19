/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledInstancesOnlyFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class CanceledInstancesOnlyQueryFilter implements QueryFilter<CanceledInstancesOnlyFilterDataDto> {

  public static String EXTERNALLY_TERMINATED = "EXTERNALLY_TERMINATED";
  public static String INTERNALLY_TERMINATED = "INTERNALLY_TERMINATED";

  public void addFilters(BoolQueryBuilder query, List<CanceledInstancesOnlyFilterDataDto> canceledInstancesOnlyFilters) {
    if (canceledInstancesOnlyFilters != null && !canceledInstancesOnlyFilters.isEmpty()) {
      List<QueryBuilder> filters = query.filter();

      BoolQueryBuilder onlyRunningInstances =
        boolQuery()
          .should(termQuery(STATE, EXTERNALLY_TERMINATED))
          .should(termQuery(STATE, INTERNALLY_TERMINATED)
        );

      filters.add(onlyRunningInstances);
    }
  }
}
