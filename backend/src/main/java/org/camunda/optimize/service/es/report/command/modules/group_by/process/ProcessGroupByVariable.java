/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.AbstractGroupByVariable;
import org.camunda.optimize.service.es.report.command.service.VariableAggregationService;
import org.camunda.optimize.service.util.InstanceIndexUtil;
import org.camunda.optimize.service.util.ProcessVariableHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByVariable extends AbstractGroupByVariable<ProcessReportDataDto> {

  public ProcessGroupByVariable(final VariableAggregationService variableAggregationService,
                                final DefinitionService definitionService) {
    super(variableAggregationService, definitionService);
  }

  private VariableGroupByValueDto getVariableGroupByDto(final ExecutionContext<ProcessReportDataDto> context) {
    return ((VariableGroupByDto) context.getReportData().getGroupBy()).getValue();
  }

  @Override
  protected String getVariableName(final ExecutionContext<ProcessReportDataDto> context) {
    return getVariableGroupByDto(context).getName();
  }

  @Override
  protected VariableType getVariableType(final ExecutionContext<ProcessReportDataDto> context) {
    return getVariableGroupByDto(context).getType();
  }

  @Override
  protected String getNestedVariableNameFieldLabel() {
    return getNestedVariableNameField();
  }

  @Override
  protected String getNestedVariableTypeField() {
    return ProcessVariableHelper.getNestedVariableTypeField();
  }

  @Override
  protected String getNestedVariableValueFieldLabel(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  @Override
  protected String getVariablePath() {
    return VARIABLES;
  }

  @Override
  protected String[] getIndexNames(final ExecutionContext<ProcessReportDataDto> context) {
    return InstanceIndexUtil.getProcessInstanceIndexAliasNames(context.getReportData());
  }

  @Override
  protected BoolQueryBuilder getVariableUndefinedOrNullQuery(final ExecutionContext<ProcessReportDataDto> context) {
    final VariableGroupByValueDto variable = getVariableGroupByDto(context);
    return ProcessVariableHelper.createFilterForUndefinedOrNullQueryBuilder(variable.getName(), variable.getType());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setGroupBy(new VariableGroupByDto());
  }

}
