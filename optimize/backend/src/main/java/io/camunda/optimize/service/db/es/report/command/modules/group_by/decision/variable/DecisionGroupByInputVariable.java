/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.decision.variable;

import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.INPUTS;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableTypeField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByInputVariableDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.AbstractGroupByVariable;
import io.camunda.optimize.service.db.es.report.command.service.VariableAggregationService;
import io.camunda.optimize.service.util.DecisionVariableHelper;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionGroupByInputVariable extends AbstractGroupByVariable<DecisionReportDataDto> {

  public DecisionGroupByInputVariable(
      final VariableAggregationService variableAggregationService,
      final DefinitionService definitionService) {
    super(variableAggregationService, definitionService);
  }

  private DecisionGroupByVariableValueDto getVariableGroupByDto(
      final ExecutionContext<DecisionReportDataDto> context) {
    return ((DecisionGroupByInputVariableDto) context.getReportData().getGroupBy()).getValue();
  }

  @Override
  protected String getVariablePath() {
    return INPUTS;
  }

  @Override
  protected String getVariableName(final ExecutionContext<DecisionReportDataDto> context) {
    return getVariableGroupByDto(context).getId();
  }

  @Override
  protected VariableType getVariableType(final ExecutionContext<DecisionReportDataDto> context) {
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
  protected String[] getIndexNames(final ExecutionContext<DecisionReportDataDto> context) {
    return InstanceIndexUtil.getDecisionInstanceIndexAliasName(context.getReportData());
  }

  @Override
  protected BoolQueryBuilder getVariableUndefinedOrNullQuery(
      final ExecutionContext<DecisionReportDataDto> context) {
    return DecisionVariableHelper.getVariableUndefinedOrNullQuery(
        getVariableName(context), getVariablePath(), getVariableType(context));
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final DecisionReportDataDto dataForCommandKey) {
    dataForCommandKey.setGroupBy(new DecisionGroupByInputVariableDto());
  }
}
