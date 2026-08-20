/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.filter.util;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import java.util.List;
import java.util.Set;

public class ModelElementFilterQueryUtil {
  private static final Set<Class<? extends ProcessFilterDto<?>>> FLOW_NODE_VIEW_LEVEL_FILTERS =
      Set.of(
          RunningFlowNodesOnlyFilterDto.class,
          CompletedFlowNodesOnlyFilterDto.class,
          CompletedOrCanceledFlowNodesOnlyFilterDto.class,
          CanceledFlowNodesOnlyFilterDto.class,
          CandidateGroupFilterDto.class,
          AssigneeFilterDto.class,
          FlowNodeDurationFilterDto.class,
          ExecutedFlowNodeFilterDto.class,
          FlowNodeStartDateFilterDto.class,
          FlowNodeEndDateFilterDto.class);

  public static List<ProcessFilterDto<?>> getViewLevelFiltersForInstanceMatch(
      final List<ProcessFilterDto<?>> filters) {
    return filters.stream()
        .filter(filter -> FilterApplicationLevel.VIEW.equals(filter.getFilterLevel()))
        .filter(filter -> FLOW_NODE_VIEW_LEVEL_FILTERS.contains(filter.getClass()))
        .toList();
  }
}
