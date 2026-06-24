/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_RAW_PROCESS_INSTANCE_DATA_GROUP_BY_NONE;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.process.RawProcessInstanceDataGroupByNoneExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class RawProcessInstanceDataGroupByNoneExecutionPlanInterpreterOS
    extends AbstractProcessExecutionPlanInterpreterOS
    implements RawProcessInstanceDataGroupByNoneExecutionPlanInterpreter {

  private final ProcessGroupByInterpreterFacadeOS groupByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final OptimizeOpenSearchClient osClient;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessQueryFilterEnhancerOS queryFilterEnhancer;

  public RawProcessInstanceDataGroupByNoneExecutionPlanInterpreterOS(
      final ProcessGroupByInterpreterFacadeOS groupByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final OptimizeOpenSearchClient osClient,
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessQueryFilterEnhancerOS queryFilterEnhancer) {
    this.groupByInterpreter = groupByInterpreter;
    this.viewInterpreter = viewInterpreter;
    this.osClient = osClient;
    this.processDefinitionReader = processDefinitionReader;
    this.queryFilterEnhancer = queryFilterEnhancer;
  }

  @Override
  public Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return Set.of(PROCESS_RAW_PROCESS_INSTANCE_DATA_GROUP_BY_NONE);
  }

  @Override
  public CommandEvaluationResult<Object> interpret(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> executionContext) {
    final CommandEvaluationResult<Object> commandResult = super.interpret(executionContext);
    addNewVariablesAndDtoFieldsToTableColumnConfig(executionContext, commandResult);
    return commandResult;
  }

  public ProcessGroupByInterpreterFacadeOS getGroupByInterpreter() {
    return this.groupByInterpreter;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }

  public OptimizeOpenSearchClient getOsClient() {
    return this.osClient;
  }

  public ProcessDefinitionReader getProcessDefinitionReader() {
    return this.processDefinitionReader;
  }

  public ProcessQueryFilterEnhancerOS getQueryFilterEnhancer() {
    return this.queryFilterEnhancer;
  }
}
