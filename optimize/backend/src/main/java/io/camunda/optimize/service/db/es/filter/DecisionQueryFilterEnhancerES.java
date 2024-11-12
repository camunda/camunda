/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.InputVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.filter.OutputVariableFilterDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.report.filter.DecisionQueryFilterEnhancer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DecisionQueryFilterEnhancerES extends DecisionQueryFilterEnhancer
    implements QueryFilterEnhancerES<DecisionFilterDto<?>> {

  private final EvaluationDateQueryFilterES evaluationDateQueryFilter;
  private final DecisionInputVariableQueryFilterES decisionInputVariableQueryFilter;
  private final DecisionOutputVariableQueryFilterES decisionOutputVariableQueryFilter;

  public DecisionQueryFilterEnhancerES(
      final EvaluationDateQueryFilterES evaluationDateQueryFilter,
      final DecisionInputVariableQueryFilterES decisionInputVariableQueryFilter,
      final DecisionOutputVariableQueryFilterES decisionOutputVariableQueryFilter) {
    this.evaluationDateQueryFilter = evaluationDateQueryFilter;
    this.decisionInputVariableQueryFilter = decisionInputVariableQueryFilter;
    this.decisionOutputVariableQueryFilter = decisionOutputVariableQueryFilter;
  }

  @Override
  public void addFilterToQuery(
      final BoolQuery.Builder query,
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

  public EvaluationDateQueryFilterES getEvaluationDateQueryFilter() {
    return this.evaluationDateQueryFilter;
  }
}
