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
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessVariableQueryFilterOS extends AbstractProcessVariableQueryFilterOS
    implements QueryFilterOS<VariableFilterDataDto<?>> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessVariableQueryFilterOS.class);

  public ProcessVariableQueryFilterOS() {}

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
