/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonCanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProcessQueryFilterEnhancer implements QueryFilterEnhancer<ProcessFilterDto> {

  private final StartDateQueryFilter startDateQueryFilter;
  private final EndDateQueryFilter endDateQueryFilter;
  private final ProcessVariableQueryFilter variableQueryFilter;
  private final ExecutedFlowNodeQueryFilter executedFlowNodeQueryFilter;
  private final DurationQueryFilter durationQueryFilter;
  private final RunningInstancesOnlyQueryFilter runningInstancesOnlyQueryFilter;
  private final CompletedInstancesOnlyQueryFilter completedInstancesOnlyQueryFilter;
  private final CanceledInstancesOnlyQueryFilter canceledInstancesOnlyQueryFilter;
  private final NonCanceledInstancesOnlyQueryFilter nonCanceledInstancesOnlyQueryFilter;

  @Override
  public void addFilterToQuery(BoolQueryBuilder query, List<ProcessFilterDto> filter) {
    if (filter != null) {
      startDateQueryFilter.addFilters(query, extractFilters(filter, StartDateFilterDto.class));
      endDateQueryFilter.addFilters(query, extractFilters(filter, EndDateFilterDto.class));
      variableQueryFilter.addFilters(query, extractFilters(filter, VariableFilterDto.class));
      executedFlowNodeQueryFilter.addFilters(query, extractFilters(filter, ExecutedFlowNodeFilterDto.class));
      durationQueryFilter.addFilters(query, extractFilters(filter, DurationFilterDto.class));
      runningInstancesOnlyQueryFilter.addFilters(query, extractFilters(filter, RunningInstancesOnlyFilterDto.class));
      completedInstancesOnlyQueryFilter.addFilters(
        query,
        extractFilters(filter, CompletedInstancesOnlyFilterDto.class)
      );
      canceledInstancesOnlyQueryFilter.addFilters(query, extractFilters(filter, CanceledInstancesOnlyFilterDto.class));
      nonCanceledInstancesOnlyQueryFilter.addFilters(
        query,
        extractFilters(filter, NonCanceledInstancesOnlyFilterDto.class)
      );
    }
  }

  public StartDateQueryFilter getStartDateQueryFilterService() {
    return startDateQueryFilter;
  }

  public EndDateQueryFilter getEndDateQueryFilter() {
    return endDateQueryFilter;
  }

  public <T extends FilterDataDto> List<T> extractFilters(List<ProcessFilterDto> filter,
                                                          Class<? extends ProcessFilterDto> clazz) {
    return filter
      .stream()
      .filter(clazz::isInstance)
      .map(dateFilter -> (T) dateFilter.getData())
      .collect(Collectors.toList());
  }
}
