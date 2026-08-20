/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessVariableQueryFilterES extends AbstractProcessVariableQueryFilterES
    implements QueryFilterES<VariableFilterDataDto<?>> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessVariableQueryFilterES.class);

  public ProcessVariableQueryFilterES() {}

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<VariableFilterDataDto<?>> variables,
      final FilterContext filterContext) {
    if (variables != null) {
      query.filter(
          variables.stream()
              .map(v -> createFilterQueryBuilder(v, filterContext.getTimezone()).build())
              .toList());
    }
  }
}
