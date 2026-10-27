/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.MultipleVariableFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessMultiVariableQueryFilterES extends AbstractProcessVariableQueryFilterES
    implements QueryFilterES<MultipleVariableFilterDataDto> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessMultiVariableQueryFilterES.class);

  public ProcessMultiVariableQueryFilterES() {}

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<MultipleVariableFilterDataDto> multiVariableFilters,
      final FilterContext filterContext) {
    if (multiVariableFilters != null) {
      query.filter(
          multiVariableFilters.stream()
              .map(
                  multiVariableFilter ->
                      buildMultiVariableFilterQuery(multiVariableFilter, filterContext).build())
              .toList());
    }
  }

  private Query.Builder buildMultiVariableFilterQuery(
      final MultipleVariableFilterDataDto multipleVariableFilter,
      final FilterContext filterContext) {
    final Query.Builder variableFilterBuilder = new Query.Builder();

    variableFilterBuilder.bool(
        b -> {
          multipleVariableFilter
              .getData()
              .forEach(
                  variableFilter ->
                      b.should(
                          createFilterQueryBuilder(variableFilter, filterContext.getTimezone())
                              .build()));
          return b;
        });

    return variableFilterBuilder;
  }
}
