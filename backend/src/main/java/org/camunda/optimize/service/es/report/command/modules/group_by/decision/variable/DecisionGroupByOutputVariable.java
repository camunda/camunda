/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.decision.variable;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByOutputVariableDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.AbstractGroupByVariable;
import org.camunda.optimize.service.es.report.command.service.VariableAggregationService;
import org.camunda.optimize.service.util.DecisionVariableHelper;
import org.camunda.optimize.service.util.InstanceIndexUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.OUTPUTS;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableTypeField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DecisionGroupByOutputVariable extends AbstractGroupByVariable<DecisionReportDataDto> {

  public DecisionGroupByOutputVariable(final VariableAggregationService variableAggregationService,
                                       final DefinitionService definitionService) {
    super(variableAggregationService, definitionService);
  }

  private DecisionGroupByVariableValueDto getVariableGroupByDto(final ExecutionContext<DecisionReportDataDto> context) {
    return ((DecisionGroupByOutputVariableDto) context.getReportData().getGroupBy()).getValue();
  }

  @Override
  protected String getVariablePath() {
    return OUTPUTS;
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
  protected BoolQueryBuilder getVariableUndefinedOrNullQuery(final ExecutionContext<DecisionReportDataDto> context) {
    return DecisionVariableHelper.getVariableUndefinedOrNullQuery(
      getVariableName(context),
      getVariablePath(),
      getVariableType(context)
    );
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto dataForCommandKey) {
    dataForCommandKey.setGroupBy(new DecisionGroupByOutputVariableDto());
  }
}
