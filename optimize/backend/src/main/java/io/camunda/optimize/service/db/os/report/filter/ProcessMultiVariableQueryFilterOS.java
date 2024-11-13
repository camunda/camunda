/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.or;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.MultipleVariableFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessMultiVariableQueryFilterOS extends AbstractProcessVariableQueryFilterOS
    implements QueryFilterOS<MultipleVariableFilterDataDto> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessMultiVariableQueryFilterOS.class);

  public ProcessMultiVariableQueryFilterOS() {}

  @Override
  public List<Query> filterQueries(
      final List<MultipleVariableFilterDataDto> multiVariableFilters,
      final FilterContext filterContext) {
    return multiVariableFilters == null
        ? List.of()
        : multiVariableFilters.stream()
            .map(
                multiVariableFilter ->
                    buildMultiVariableFilterQuery(multiVariableFilter, filterContext))
            .toList();
  }

  private Query buildMultiVariableFilterQuery(
      final MultipleVariableFilterDataDto multipleVariableFilter,
      final FilterContext filterContext) {
    final List<Query> queries =
        multipleVariableFilter.getData().stream()
            .map(variableFilter -> createFilterQuery(variableFilter, filterContext.getTimezone()))
            .toList();

    return or(queries);
  }
}
