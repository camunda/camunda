/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.db.es.filter.util.DateFilterQueryUtilES;
import io.camunda.optimize.service.db.filter.AgentRunFilters;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Applies the process filters an agentRun report accepts to the agent run index's own fields. */
@Component
@Conditional(ElasticSearchCondition.class)
public class AgentRunQueryFilterEnhancerES implements InstanceDateQueryFilterEnhancerES {

  private static final QueryFilterES<DateFilterDataDto<?>> START_DATE_FILTER =
      (query, filters, filterContext) ->
          DateFilterQueryUtilES.addFilters(
              query, filters, AgentRunIndex.START_DATE, filterContext.getTimezone());
  private static final QueryFilterES<DateFilterDataDto<?>> END_DATE_FILTER =
      (query, filters, filterContext) ->
          DateFilterQueryUtilES.addFilters(
              query, filters, AgentRunIndex.END_DATE, filterContext.getTimezone());

  @Override
  public void addFilterToQuery(
      final BoolQuery.Builder query,
      final List<ProcessFilterDto<?>> filters,
      final FilterContext filterContext) {
    AgentRunFilters.validate(filters);
    END_DATE_FILTER.addFilters(query, AgentRunFilters.endDateFilters(filters), filterContext);
    if (AgentRunFilters.isCompletedOnly(filters)) {
      query.filter(f -> f.exists(e -> e.field(AgentRunIndex.END_DATE)));
    }
  }

  @Override
  public <T extends FilterDataDto> List<T> extractInstanceFilters(
      final List<ProcessFilterDto<?>> filters, final Class<? extends ProcessFilterDto<T>> clazz) {
    return AgentRunFilters.instanceFilterData(filters, clazz);
  }

  @Override
  public QueryFilterES<DateFilterDataDto<?>> getInstanceStartDateQueryFilter() {
    return START_DATE_FILTER;
  }

  @Override
  public QueryFilterES<DateFilterDataDto<?>> getInstanceEndDateQueryFilter() {
    return END_DATE_FILTER;
  }
}
