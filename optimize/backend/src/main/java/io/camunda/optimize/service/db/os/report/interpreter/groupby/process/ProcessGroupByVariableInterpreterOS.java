/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_VARIABLE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.AbstractGroupByVariableInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS;
import io.camunda.optimize.service.db.os.util.ProcessVariableHelperOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.util.ProcessVariableHelper;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByVariableInterpreterOS
    extends AbstractGroupByVariableInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan>
    implements ProcessGroupByInterpreterOS {

  private final VariableAggregationServiceOS variableAggregationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByVariableInterpreterOS(
      final VariableAggregationServiceOS variableAggregationService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.variableAggregationService = variableAggregationService;
    this.definitionService = definitionService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_VARIABLE);
  }

  private VariableGroupByValueDto getVariableGroupByDto(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return ((VariableGroupByDto) context.getReportData().getGroupBy()).getValue();
  }

  @Override
  protected String getVariableName(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return getVariableGroupByDto(context).getName();
  }

  @Override
  protected VariableType getVariableType(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return getVariableGroupByDto(context).getType();
  }

  @Override
  protected String getNestedVariableNameFieldLabel() {
    return ProcessVariableHelper.getNestedVariableNameField();
  }

  @Override
  protected String getNestedVariableTypeField() {
    return ProcessVariableHelper.getNestedVariableTypeField();
  }

  @Override
  protected String getNestedVariableValueFieldLabel(final VariableType type) {
    return ProcessVariableHelper.getNestedVariableValueFieldForType(type);
  }

  @Override
  protected String getVariablePath() {
    return VARIABLES;
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return InstanceIndexUtil.getProcessInstanceIndexAliasNames(context.getReportData());
  }

  @Override
  protected Query getVariableUndefinedOrNullQuery(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final VariableGroupByValueDto variable = getVariableGroupByDto(context);
    return ProcessVariableHelperOS.createFilterForUndefinedOrNullQuery(
        variable.getName(), variable.getType());
  }

  public VariableAggregationServiceOS getVariableAggregationService() {
    return this.variableAggregationService;
  }

  public DefinitionService getDefinitionService() {
    return this.definitionService;
  }

  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }
}
