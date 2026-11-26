/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.filter;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.util.List;

/**
 * The process filters an agentRun report accepts. Each one is applied to the agent run's own
 * fields; every other process filter reads fields agent runs do not have, so it is rejected.
 */
public final class AgentRunFilters {

  public static final List<Class<? extends ProcessFilterDto<?>>> SUPPORTED_FILTERS =
      List.of(InstanceEndDateFilterDto.class, CompletedInstancesOnlyFilterDto.class);

  private AgentRunFilters() {}

  public static void validate(final List<ProcessFilterDto<?>> filters) {
    filters.stream()
        .filter(filter -> !SUPPORTED_FILTERS.contains(filter.getClass()))
        .findFirst()
        .ifPresent(
            filter -> {
              throw new OptimizeValidationException(
                  String.format(
                      "The filter [%s] is not supported for agentRun views. Supported filters: "
                          + "instanceEndDate, completedInstancesOnly.",
                      filter.getClass().getSimpleName()));
            });
  }

  public static List<DateFilterDataDto<?>> endDateFilters(final List<ProcessFilterDto<?>> filters) {
    return instanceFilterData(filters, InstanceEndDateFilterDto.class);
  }

  public static boolean isCompletedOnly(final List<ProcessFilterDto<?>> filters) {
    return !instanceFilterData(filters, CompletedInstancesOnlyFilterDto.class).isEmpty();
  }

  // Same selection as the process filter enhancers: only instance-level filters apply here.
  @SuppressWarnings("unchecked")
  private static <T extends FilterDataDto> List<T> instanceFilterData(
      final List<ProcessFilterDto<?>> filters, final Class<? extends ProcessFilterDto<T>> clazz) {
    return filters.stream()
        .filter(clazz::isInstance)
        .filter(filter -> FilterApplicationLevel.INSTANCE.equals(filter.getFilterLevel()))
        .map(filter -> (T) filter.getData())
        .toList();
  }
}
