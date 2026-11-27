/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.db.filter.AgentRunFilters;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.os.report.filter.util.DateFilterQueryUtilOS;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Applies the process filters an agentRun report accepts to the agent run index's own fields. */
@Component
@Conditional(OpenSearchCondition.class)
public class AgentRunQueryFilterEnhancerOS implements InstanceDateQueryFilterEnhancerOS {

  private static final QueryFilterOS<DateFilterDataDto<?>> START_DATE_FILTER =
      (filters, filterContext) ->
          DateFilterQueryUtilOS.filterQueries(
              filters, AgentRunIndex.START_DATE, filterContext.getTimezone());
  private static final QueryFilterOS<DateFilterDataDto<?>> END_DATE_FILTER =
      (filters, filterContext) ->
          DateFilterQueryUtilOS.filterQueries(
              filters, AgentRunIndex.END_DATE, filterContext.getTimezone());

  @Override
  public List<Query> filterQueries(
      final List<ProcessFilterDto<?>> filters, final FilterContext filterContext) {
    AgentRunFilters.validate(filters);
    final List<Query> queries =
        new ArrayList<>(
            END_DATE_FILTER.filterQueries(AgentRunFilters.endDateFilters(filters), filterContext));
    if (AgentRunFilters.isCompletedOnly(filters)) {
      queries.add(exists(AgentRunIndex.END_DATE));
    }
    return queries;
  }

  @Override
  public <T extends FilterDataDto> List<T> extractInstanceFilters(
      final List<ProcessFilterDto<?>> filters, final Class<? extends ProcessFilterDto<T>> clazz) {
    return AgentRunFilters.instanceFilterData(filters, clazz);
  }

  @Override
  public QueryFilterOS<DateFilterDataDto<?>> getInstanceStartDateQueryFilter() {
    return START_DATE_FILTER;
  }

  @Override
  public QueryFilterOS<DateFilterDataDto<?>> getInstanceEndDateQueryFilter() {
    return END_DATE_FILTER;
  }
}
