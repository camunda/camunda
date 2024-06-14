/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.es.filter;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.MultipleVariableFilterDataDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
public class ProcessMultiVariableQueryFilter extends AbstractProcessVariableQueryFilter
    implements QueryFilter<MultipleVariableFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<MultipleVariableFilterDataDto> multiVariableFilters,
      final FilterContext filterContext) {
    if (multiVariableFilters != null) {
      List<QueryBuilder> filters = query.filter();
      for (MultipleVariableFilterDataDto multiVariableFilter : multiVariableFilters) {
        filters.add(buildMultiVariableFilterQuery(multiVariableFilter, filterContext));
      }
    }
  }

  private QueryBuilder buildMultiVariableFilterQuery(
      final MultipleVariableFilterDataDto multipleVariableFilter,
      final FilterContext filterContext) {
    final BoolQueryBuilder variableFilterBuilder = boolQuery();
    multipleVariableFilter
        .getData()
        .forEach(
            variableFilter ->
                variableFilterBuilder.should(
                    createFilterQueryBuilder(variableFilter, filterContext.getTimezone())));
    return variableFilterBuilder;
  }
}
