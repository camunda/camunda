/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.MultipleVariableFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

@RequiredArgsConstructor
@Slf4j
@Component
public class ProcessMultiVariableQueryFilter extends AbstractProcessVariableQueryFilter
  implements QueryFilter<MultipleVariableFilterDataDto> {

  @Override
  public void addFilters(final BoolQueryBuilder query, final List<MultipleVariableFilterDataDto> multiVariableFilters,
                         final FilterContext filterContext) {
    if (multiVariableFilters != null) {
      List<QueryBuilder> filters = query.filter();
      for (MultipleVariableFilterDataDto multiVariableFilter : multiVariableFilters) {
        filters.add(buildMultiVariableFilterQuery(multiVariableFilter, filterContext));
      }
    }
  }

  private QueryBuilder buildMultiVariableFilterQuery(final MultipleVariableFilterDataDto multipleVariableFilter,
                                                     final FilterContext filterContext) {
    final BoolQueryBuilder variableFilterBuilder = boolQuery();
    multipleVariableFilter.getData()
      .forEach(variableFilter -> variableFilterBuilder.should(createFilterQueryBuilder(
        variableFilter,
        filterContext.getTimezone()
      )));
    return variableFilterBuilder;
  }

}
