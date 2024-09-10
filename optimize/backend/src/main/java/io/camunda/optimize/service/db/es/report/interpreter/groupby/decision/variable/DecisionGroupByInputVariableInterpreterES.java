/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.decision.variable;

import static io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy.DECISION_GROUP_BY_INPUT_VARIABLE;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.INPUTS;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableTypeField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByInputVariableDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.decision.DecisionDistributedByNoneInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.AbstractGroupByVariableInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.decision.DecisionGroupByInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.decision.DecisionViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES;
import io.camunda.optimize.service.db.es.util.DecisionVariableHelperES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class DecisionGroupByInputVariableInterpreterES
    extends AbstractGroupByVariableInterpreterES<DecisionReportDataDto, DecisionExecutionPlan>
    implements DecisionGroupByInterpreterES {
  @Getter private final VariableAggregationServiceES variableAggregationService;
  @Getter private final DefinitionService definitionService;
  @Getter private final DecisionDistributedByNoneInterpreterES distributedByInterpreter;
  @Getter private final DecisionViewInterpreterFacadeES viewInterpreter;

  @Override
  public Set<DecisionGroupBy> getSupportedGroupBys() {
    return Set.of(DECISION_GROUP_BY_INPUT_VARIABLE);
  }

  private DecisionGroupByVariableValueDto getVariableGroupByDto(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return ((DecisionGroupByInputVariableDto) context.getReportData().getGroupBy()).getValue();
  }

  @Override
  protected String getVariablePath() {
    return INPUTS;
  }

  @Override
  protected String getVariableName(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return getVariableGroupByDto(context).getId();
  }

  @Override
  protected VariableType getVariableType(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return getVariableGroupByDto(context).getType();
  }

  @Override
  protected String getNestedVariableNameFieldLabel() {
    return getVariableClauseIdField(getVariablePath());
  }

  @Override
  protected String getNestedVariableTypeField() {
    return getVariableTypeField(getVariablePath());
  }

  @Override
  protected String getNestedVariableValueFieldLabel(final VariableType type) {
    return getVariableValueFieldForType(getVariablePath(), type);
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return InstanceIndexUtil.getDecisionInstanceIndexAliasName(context.getReportData());
  }

  @Override
  protected BoolQueryBuilder getVariableUndefinedOrNullQuery(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return DecisionVariableHelperES.getVariableUndefinedOrNullQuery(
        getVariableName(context), getVariablePath(), getVariableType(context));
  }
}
