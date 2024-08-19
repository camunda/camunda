/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class DecisionQueryFilterEnhancer implements QueryFilterEnhancer<DecisionFilterDto<?>> {

  private final EvaluationDateQueryFilter evaluationDateQueryFilter;
  private final DecisionInputVariableQueryFilter decisionInputVariableQueryFilter;
  private final DecisionOutputVariableQueryFilter decisionOutputVariableQueryFilter;

  public DecisionQueryFilterEnhancer(
      final EvaluationDateQueryFilter evaluationDateQueryFilter,
      final DecisionInputVariableQueryFilter decisionInputVariableQueryFilter,
      final DecisionOutputVariableQueryFilter decisionOutputVariableQueryFilter) {
    this.evaluationDateQueryFilter = evaluationDateQueryFilter;
    this.decisionInputVariableQueryFilter = decisionInputVariableQueryFilter;
    this.decisionOutputVariableQueryFilter = decisionOutputVariableQueryFilter;
  }

  @Override
  public void addFilterToQuery(
      final BoolQueryBuilder query,
      final List<DecisionFilterDto<?>> filter,
      final FilterContext filterContext) {
    if (filter != null) {
      evaluationDateQueryFilter.addFilters(
          query, extractFilters(filter, EvaluationDateFilterDto.class), filterContext);
      decisionInputVariableQueryFilter.addFilters(
          query, extractFilters(filter, InputVariableFilterDto.class), filterContext);
      decisionOutputVariableQueryFilter.addFilters(
          query, extractFilters(filter, OutputVariableFilterDto.class), filterContext);
    }
  }

  public EvaluationDateQueryFilter getEvaluationDateQueryFilter() {
    return evaluationDateQueryFilter;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends FilterDataDto> List<T> extractFilters(
      final List<DecisionFilterDto<?>> filter,
      final Class<? extends DecisionFilterDto<?>> filterClass) {
    return filter.stream()
        .filter(filterClass::isInstance)
        .map(dateFilter -> (T) dateFilter.getData())
        .collect(Collectors.toList());
  }
}
