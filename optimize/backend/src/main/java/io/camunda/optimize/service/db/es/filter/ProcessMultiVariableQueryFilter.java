/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.MultipleVariableFilterDataDto;
import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProcessMultiVariableQueryFilter extends AbstractProcessVariableQueryFilter
    implements QueryFilter<MultipleVariableFilterDataDto> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessMultiVariableQueryFilter.class);

  public ProcessMultiVariableQueryFilter() {}

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<MultipleVariableFilterDataDto> multiVariableFilters,
      final FilterContext filterContext) {
    if (multiVariableFilters != null) {
      final List<QueryBuilder> filters = query.filter();
      for (final MultipleVariableFilterDataDto multiVariableFilter : multiVariableFilters) {
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
