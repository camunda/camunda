/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE;
import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.INTERNALLY_TERMINATED_STATE;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.or;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.CanceledInstancesOnlyFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class CanceledInstancesOnlyQueryFilterOS
    implements QueryFilterOS<CanceledInstancesOnlyFilterDataDto> {

  @Override
  public List<Query> filterQueries(
      final List<CanceledInstancesOnlyFilterDataDto> canceledInstancesOnlyFilters,
      final FilterContext filterContext) {
    return canceledInstancesOnlyFilters != null && !canceledInstancesOnlyFilters.isEmpty()
        ? List.of(
            or(term(STATE, EXTERNALLY_TERMINATED_STATE), term(STATE, INTERNALLY_TERMINATED_STATE)))
        : List.of();
  }
}
