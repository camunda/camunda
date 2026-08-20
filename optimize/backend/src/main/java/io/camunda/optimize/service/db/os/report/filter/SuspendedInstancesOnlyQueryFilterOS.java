/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.SuspendedInstancesOnlyFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class SuspendedInstancesOnlyQueryFilterOS
    implements QueryFilterOS<SuspendedInstancesOnlyFilterDataDto> {

  @Override
  public List<Query> filterQueries(
      final List<SuspendedInstancesOnlyFilterDataDto> suspendedInstancesOnlyFilters,
      final FilterContext filterContext) {
    return suspendedInstancesOnlyFilters != null && !suspendedInstancesOnlyFilters.isEmpty()
        ? List.of(term(STATE, SUSPENDED_STATE))
        : List.of();
  }
}
