/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.group_by.process;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.AbstractGroupByVariable;
import io.camunda.optimize.service.db.es.report.command.service.VariableAggregationService;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import io.camunda.optimize.service.util.ProcessVariableHelper;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByVariable extends AbstractGroupByVariable<ProcessReportDataDto> {

  public ProcessGroupByVariable(
      final VariableAggregationService variableAggregationService,
      final DefinitionService definitionService) {
    super(variableAggregationService, definitionService);
  }

  private VariableGroupByValueDto getVariableGroupByDto(
      final ExecutionContext<ProcessReportDataDto> context) {
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
  protected BoolQueryBuilder getVariableUndefinedOrNullQuery(
      final ExecutionContext<ProcessReportDataDto> context) {
    final VariableGroupByValueDto variable = getVariableGroupByDto(context);
    return ProcessVariableHelper.createFilterForUndefinedOrNullQueryBuilder(
        variable.getName(), variable.getType());
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setGroupBy(new VariableGroupByDto());
  }
}
