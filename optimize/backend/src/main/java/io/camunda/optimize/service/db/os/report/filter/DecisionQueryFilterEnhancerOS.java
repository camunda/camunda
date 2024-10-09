/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.report.filter.DecisionQueryFilterEnhancer;
import java.util.List;
import java.util.stream.Stream;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.stereotype.Component;

@Component
public class DecisionQueryFilterEnhancerOS extends DecisionQueryFilterEnhancer
    implements QueryFilterEnhancerOS<DecisionFilterDto<?>> {

  private final EvaluationDateQueryFilterOS evaluationDateQueryFilter;
  private final DecisionInputVariableQueryFilterOS decisionInputVariableQueryFilter;
  private final DecisionOutputVariableQueryFilterOS decisionOutputVariableQueryFilter;

  public DecisionQueryFilterEnhancerOS(
      EvaluationDateQueryFilterOS evaluationDateQueryFilter,
      DecisionInputVariableQueryFilterOS decisionInputVariableQueryFilter,
      DecisionOutputVariableQueryFilterOS decisionOutputVariableQueryFilter) {
    this.evaluationDateQueryFilter = evaluationDateQueryFilter;
    this.decisionInputVariableQueryFilter = decisionInputVariableQueryFilter;
    this.decisionOutputVariableQueryFilter = decisionOutputVariableQueryFilter;
  }

  @Override
  public List<Query> filterQueries(
      final List<DecisionFilterDto<?>> filter, final FilterContext filterContext) {
    return filter == null
        ? List.of()
        : Stream.of(
                evaluationDateQueryFilter.filterQueries(
                    extractFilters(filter, EvaluationDateFilterDto.class), filterContext),
                decisionInputVariableQueryFilter.filterQueries(
                    extractFilters(filter, InputVariableFilterDto.class), filterContext),
                decisionOutputVariableQueryFilter.filterQueries(
                    extractFilters(filter, OutputVariableFilterDto.class), filterContext))
            .flatMap(List::stream)
            .toList();
  }

  public EvaluationDateQueryFilterOS getEvaluationDateQueryFilter() {
    return this.evaluationDateQueryFilter;
  }
}
