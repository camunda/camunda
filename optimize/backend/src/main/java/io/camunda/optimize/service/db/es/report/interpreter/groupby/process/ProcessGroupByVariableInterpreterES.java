/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_VARIABLE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueFieldForType;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.AbstractGroupByVariableInterpreterES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES;
import io.camunda.optimize.service.db.es.util.ProcessVariableHelperES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.util.ProcessVariableHelper;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessGroupByVariableInterpreterES
    extends AbstractGroupByVariableInterpreterES<ProcessReportDataDto, ProcessExecutionPlan>
    implements ProcessGroupByInterpreterES {

  private final VariableAggregationServiceES variableAggregationService;
  private final DefinitionService definitionService;
  private final ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  private final ProcessViewInterpreterFacadeES viewInterpreter;

  public ProcessGroupByVariableInterpreterES(
      final VariableAggregationServiceES variableAggregationService,
      final DefinitionService definitionService,
      final ProcessDistributedByInterpreterFacadeES distributedByInterpreter,
      final ProcessViewInterpreterFacadeES viewInterpreter) {
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
  protected String[] getIndexNames(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return InstanceIndexUtil.getProcessInstanceIndexAliasNames(context.getReportData());
  }

  @Override
  protected BoolQuery.Builder getVariableUndefinedOrNullQuery(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final VariableGroupByValueDto variable = getVariableGroupByDto(context);
    return ProcessVariableHelperES.createFilterForUndefinedOrNullQueryBuilder(
        variable.getName(), variable.getType());
  }

  public VariableAggregationServiceES getVariableAggregationService() {
    return this.variableAggregationService;
  }

  public DefinitionService getDefinitionService() {
    return this.definitionService;
  }

  public ProcessDistributedByInterpreterFacadeES getDistributedByInterpreter() {
    return this.distributedByInterpreter;
  }

  public ProcessViewInterpreterFacadeES getViewInterpreter() {
    return this.viewInterpreter;
  }
}
