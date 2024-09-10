/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Slf4j
@Component
@Conditional(OpenSearchCondition.class)
public class ProcessVariableQueryFilterOS extends AbstractProcessVariableQueryFilterOS
    implements QueryFilterOS<VariableFilterDataDto<?>> {

  @Override
  public List<Query> filterQueries(
      final List<VariableFilterDataDto<?>> variables, final FilterContext filterContext) {
    return variables == null
        ? List.of()
        : variables.stream()
            .map(variable -> createFilterQuery(variable, filterContext.getTimezone()))
            .toList();
  }
}
