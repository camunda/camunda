/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.STATE;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.NonSuspendedInstancesOnlyFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class NonSuspendedInstancesOnlyQueryFilterES
    implements QueryFilterES<NonSuspendedInstancesOnlyFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<NonSuspendedInstancesOnlyFilterDataDto> nonSuspendedInstancesOnlyFilters,
      final FilterContext filterContext) {
    if (nonSuspendedInstancesOnlyFilters != null && !nonSuspendedInstancesOnlyFilters.isEmpty()) {
      query.filter(
          f -> f.bool(b -> b.mustNot(m -> m.term(t -> t.field(STATE).value(SUSPENDED_STATE)))));
    }
  }
}
