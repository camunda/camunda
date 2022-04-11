/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Component
public class ProcessVariableQueryFilter extends AbstractProcessVariableQueryFilter
  implements QueryFilter<VariableFilterDataDto<?>> {

  @Override
  public void addFilters(final BoolQueryBuilder query,
                         final List<VariableFilterDataDto<?>> variables,
                         final FilterContext filterContext) {
    if (variables != null) {
      List<QueryBuilder> filters = query.filter();
      for (VariableFilterDataDto<?> variable : variables) {
        filters.add(createFilterQueryBuilder(variable, filterContext.getTimezone()));
      }
    }
  }

}
